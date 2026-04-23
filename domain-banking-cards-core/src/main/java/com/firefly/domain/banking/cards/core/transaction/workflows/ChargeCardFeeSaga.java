package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineFeeDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.ChargeCardFeeCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.COMPENSATE_REVERSE_FEE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_FEE_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_FEE_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_FEE_WAIVED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.EVENT_CARD_RESOLVED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.EVENT_FEE_POSTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.EVENT_STATEMENT_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.SAGA_CHARGE_FEE_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_POST_FEE_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_RESOLVE_CARD;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_UPDATE_STATEMENT;

/**
 * Saga orchestrator that charges a fee (annual, late-payment, cash advance, foreign-transaction)
 * against the cardholder account. When {@code waived = true} the ledger post is skipped and
 * the step returns {@code RESULT_SKIPPED}, but the step still emits the event so downstream
 * reporting can track waived fees.
 */
@Slf4j
@Saga(name = SAGA_CHARGE_FEE_NAME)
@Service
public class ChargeCardFeeSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_FEE = "FEE";
    private static final String LINE_TYPE_FEE = "FEE";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String DESC_FEE = "Credit-card fee charge";
    private static final String DESC_FEE_REVERSAL = "Compensation — reversal of credit-card fee charge";

    private final CommandBus commandBus;
    private final CardsApi cardsApi;
    private final LedgerGlProperties ledgerGlProperties;

    public ChargeCardFeeSaga(CommandBus commandBus,
                             CardsApi cardsApi,
                             LedgerGlProperties ledgerGlProperties) {
        this.commandBus = commandBus;
        this.cardsApi = cardsApi;
        this.ledgerGlProperties = ledgerGlProperties;
    }

    @SagaStep(id = STEP_RESOLVE_CARD)
    @StepEvent(type = EVENT_CARD_RESOLVED)
    public Mono<UUID> resolveCard(ChargeCardFeeCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_FEE_AMOUNT, cmd.getFeeAmount());
        ctx.putVariable(CTX_FEE_WAIVED, Boolean.TRUE.equals(cmd.getWaived()));
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

    @SagaStep(id = STEP_POST_FEE_LEDGER, compensate = COMPENSATE_REVERSE_FEE, dependsOn = STEP_RESOLVE_CARD)
    @StepEvent(type = EVENT_FEE_POSTED)
    public Mono<Object> postFeeLedger(ChargeCardFeeCommand cmd,
                                      @CorrelationId String sagaId,
                                      ExecutionContext ctx) {
        Boolean waived = ctx.getVariableAs(CTX_FEE_WAIVED, Boolean.class);
        if (Boolean.TRUE.equals(waived)) {
            log.info("Fee waived — skipping ledger post sagaId={} cardId={} feeType={}",
                    sagaId, ctx.getVariable(CTX_CARD_ID), cmd == null ? null : cmd.getFeeType());
            return Mono.just(RESULT_SKIPPED);
        }

        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_FEE_AMOUNT, BigDecimal.class);
        UUID feeIncomeGl = ledgerGlProperties.getFeeIncomeAccountId();

        TransactionLineFeeDTO lineDto = new TransactionLineFeeDTO()
                .feeType(cmd == null ? null : cmd.getFeeType())
                .feeReference(sagaId)
                .feeCalculationMethod(cmd == null ? null : cmd.getFeeCalculationMethod())
                .feeFixedAmount(amount)
                .feeCurrency(currency)
                .feeWaived(Boolean.FALSE)
                .feeWaiverReason(null);

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand ledgerCmd = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + STEP_POST_FEE_LEDGER)
                .transactionType(TX_TYPE_FEE)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_FEE)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(feeIncomeGl).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .lineType(LINE_TYPE_FEE)
                .lineDto(lineDto)
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(ledgerCmd)
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_FEE_LEDGER_TX_ID, id))
                .map(id -> (Object) id);
    }

    /** Compensation — reverses the fee post when downstream fails. Null-safe when skipped. */
    public Mono<Void> reverseFeeLedger(Object ignoredResult, @CorrelationId String sagaId, ExecutionContext ctx) {
        UUID feeLedgerTxId = (UUID) ctx.getVariable(CTX_FEE_LEDGER_TX_ID);
        if (feeLedgerTxId == null) {
            return Mono.empty();
        }
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_FEE_AMOUNT, BigDecimal.class);
        UUID feeIncomeGl = ledgerGlProperties.getFeeIncomeAccountId();

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand reversal = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + COMPENSATE_REVERSE_FEE)
                .transactionType(TX_TYPE_FEE)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_FEE_REVERSAL)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(feeIncomeGl).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .relatedTransactionId(feeLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();
        return commandBus.<PostLedgerTransactionResult>send(reversal).then();
    }

    @SagaStep(id = STEP_UPDATE_STATEMENT, dependsOn = STEP_POST_FEE_LEDGER)
    @StepEvent(type = EVENT_STATEMENT_UPDATED)
    public Mono<Object> updateStatement(ChargeCardFeeCommand ignoredCmd, ExecutionContext ctx) {
        log.info("Statement update skipped — core-banking-cards SDK does not expose a statements mutation API");
        return Mono.just(RESULT_SKIPPED);
    }
}
