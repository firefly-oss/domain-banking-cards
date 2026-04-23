package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineInterestDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.AccrueCardInterestCommand;
import com.firefly.domain.banking.cards.infra.properties.LedgerGlProperties;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.argument.CorrelationId;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.COMPENSATE_REVERSE_INTEREST;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_INTEREST_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_INTEREST_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.EVENT_CARD_RESOLVED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.EVENT_INTEREST_POSTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.EVENT_STATEMENT_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.SAGA_ACCRUE_INTEREST_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_POST_INTEREST_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_RESOLVE_CARD;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_UPDATE_STATEMENT;

/**
 * Saga orchestrator that posts an interest accrual on a credit card. Typically invoked by
 * the batch scheduler for every statement period. Steps:
 *
 * <ol>
 *   <li>{@code STEP_RESOLVE_CARD} — loads the card to obtain {@code accountId}/currency.</li>
 *   <li>{@code STEP_POST_INTEREST_LEDGER} — DEBIT cardholder account / CREDIT interest-income
 *       GL, with a typed {@link TransactionLineInterestDTO} carrying the rate and period.</li>
 *   <li>{@code STEP_UPDATE_STATEMENT} — currently a no-op because the SDK does not expose a
 *       statements mutation API; returns {@code RESULT_SKIPPED}.</li>
 * </ol>
 */
@Slf4j
@Saga(name = SAGA_ACCRUE_INTEREST_NAME)
@Service
public class AccrueCardInterestSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_INTEREST = "INTEREST";
    private static final String LINE_TYPE_INTEREST = "INTEREST";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String DESC_INTEREST = "Credit-card interest accrual";
    private static final String DESC_INTEREST_REVERSAL = "Compensation — reversal of credit-card interest accrual";
    private static final String INTEREST_TYPE_CARD = "CREDIT_CARD";

    private final CommandBus commandBus;
    private final CardsApi cardsApi;
    private final LedgerGlProperties ledgerGlProperties;

    public AccrueCardInterestSaga(CommandBus commandBus,
                                  CardsApi cardsApi,
                                  LedgerGlProperties ledgerGlProperties) {
        this.commandBus = commandBus;
        this.cardsApi = cardsApi;
        this.ledgerGlProperties = ledgerGlProperties;
    }

    @SagaStep(id = STEP_RESOLVE_CARD)
    @StepEvent(type = EVENT_CARD_RESOLVED)
    public Mono<UUID> resolveCard(AccrueCardInterestCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_INTEREST_AMOUNT, cmd.getInterestAmount());
        return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                .flatMap(card -> {
                    if (card.getAccountId() == null) {
                        return Mono.error(new IllegalStateException(
                                "card " + cmd.getCardId() + " has no linked accountId"));
                    }
                    ctx.putVariable(CTX_ACCOUNT_ID, card.getAccountId());
                    ctx.putVariable(CTX_ACCOUNT_CURRENCY, card.getCurrencyCode());
                    return Mono.just(card.getAccountId());
                });
    }

    @SagaStep(id = STEP_POST_INTEREST_LEDGER, compensate = COMPENSATE_REVERSE_INTEREST, dependsOn = STEP_RESOLVE_CARD)
    @StepEvent(type = EVENT_INTEREST_POSTED)
    public Mono<UUID> postInterestLedger(AccrueCardInterestCommand cmd,
                                         @CorrelationId String sagaId,
                                         ExecutionContext ctx) {
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_INTEREST_AMOUNT, BigDecimal.class);
        UUID interestIncomeGl = ledgerGlProperties.getInterestIncomeAccountId();

        TransactionLineInterestDTO lineDto = new TransactionLineInterestDTO()
                .interestType(INTEREST_TYPE_CARD)
                .interestReference(sagaId)
                .interestRatePercentage(cmd == null ? null : cmd.getInterestRate())
                .interestCalculationMethod(cmd == null ? null : cmd.getCalculationMethod())
                .interestAccrualStartDate(cmd == null ? null : cmd.getPeriodStart())
                .interestAccrualEndDate(cmd == null ? null : cmd.getPeriodEnd())
                .interestCurrency(currency);

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand ledgerCmd = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + STEP_POST_INTEREST_LEDGER)
                .transactionType(TX_TYPE_INTEREST)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_INTEREST)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(interestIncomeGl).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .lineType(LINE_TYPE_INTEREST)
                .lineDto(lineDto)
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(ledgerCmd)
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_INTEREST_LEDGER_TX_ID, id));
    }

    /** Compensation — posts a REVERSAL against the interest accrual when downstream fails. */
    public Mono<Void> reverseInterestLedger(UUID ignoredResult, @CorrelationId String sagaId, ExecutionContext ctx) {
        UUID interestLedgerTxId = (UUID) ctx.getVariable(CTX_INTEREST_LEDGER_TX_ID);
        if (interestLedgerTxId == null) {
            return Mono.empty();
        }
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_INTEREST_AMOUNT, BigDecimal.class);
        UUID interestIncomeGl = ledgerGlProperties.getInterestIncomeAccountId();

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand reversal = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + COMPENSATE_REVERSE_INTEREST)
                .transactionType(TX_TYPE_INTEREST)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_INTEREST_REVERSAL)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(interestIncomeGl).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .relatedTransactionId(interestLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();
        return commandBus.<PostLedgerTransactionResult>send(reversal).then();
    }

    @SagaStep(id = STEP_UPDATE_STATEMENT, dependsOn = STEP_POST_INTEREST_LEDGER)
    @StepEvent(type = EVENT_STATEMENT_UPDATED)
    public Mono<Object> updateStatement(AccrueCardInterestCommand ignoredCmd, ExecutionContext ctx) {
        log.info("Statement update skipped — core-banking-cards SDK does not expose a statements mutation API");
        return Mono.just(RESULT_SKIPPED);
    }
}
