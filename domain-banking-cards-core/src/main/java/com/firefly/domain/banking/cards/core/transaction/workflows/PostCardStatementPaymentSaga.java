package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardPaymentsApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.core.banking.cards.sdk.model.CardPaymentDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.model.PaginationResponse;
import com.firefly.core.banking.ledger.sdk.model.TransactionLegDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineTransferDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.PostCardStatementPaymentCommand;
import com.firefly.domain.banking.cards.infra.properties.LedgerGlProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.argument.CorrelationId;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.COMPENSATE_FAIL_PAYMENT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.COMPENSATE_REVERSE_PAYMENT_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_CARD_PAYMENT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_FUNDING_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_IS_AUTO;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_IS_FULL;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_PAYMENT_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_PAYMENT_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_PAYMENT_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_STATEMENT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.EVENT_CARD_BALANCE_REFRESHED;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.EVENT_CARD_PAYMENT_CREATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.EVENT_CARD_PAYMENT_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.EVENT_PAYMENT_LEDGER_POSTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.EVENT_STATEMENT_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.SAGA_POST_STATEMENT_PAYMENT_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_CREATE_CARD_PAYMENT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_POST_PAYMENT_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_REFRESH_CARD_BALANCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_UPDATE_CARD_PAYMENT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_UPDATE_STATEMENT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.TRANSFER_PURPOSE_STATEMENT_PAYMENT;

/**
 * Saga orchestrator for a credit-card statement payment from a funding account. Steps:
 *
 * <ol>
 *   <li>{@code STEP_CREATE_CARD_PAYMENT} — records a PENDING {@code card_payment} row.</li>
 *   <li>{@code STEP_POST_PAYMENT_LEDGER} — posts a POSTED TRANSFER ledger transaction:
 *       DEBIT funding account / CREDIT credit-card receivable GL.</li>
 *   <li>{@code STEP_UPDATE_CARD_PAYMENT} — marks the {@code card_payment} as COMPLETED.</li>
 *   <li>{@code STEP_UPDATE_STATEMENT} — currently a no-op (SDK does not expose a statements
 *       mutation API); returns {@code RESULT_SKIPPED}. Callers integrate statement updates
 *       via downstream event consumers when a statement API is available.</li>
 *   <li>{@code STEP_REFRESH_CARD_BALANCE} — recomputes the ledger balance projection.</li>
 * </ol>
 */
@Slf4j
@Saga(name = SAGA_POST_STATEMENT_PAYMENT_NAME)
@Service
public class PostCardStatementPaymentSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_TRANSFER = "TRANSFER";
    private static final String LINE_TYPE_TRANSFER = "TRANSFER";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String PAYMENT_STATUS_PENDING = "PENDING";
    private static final String PAYMENT_STATUS_COMPLETED = "COMPLETED";
    private static final String PAYMENT_STATUS_FAILED = "FAILED";
    private static final String DESC_PAYMENT = "Credit-card statement payment";
    private static final String DESC_PAYMENT_REVERSAL = "Compensation — reversal of credit-card statement payment";
    private static final String BALANCE_TYPE_LEDGER = "LEDGER";

    private final CommandBus commandBus;
    private final CardPaymentsApi cardPaymentsApi;
    private final CardBalancesApi cardBalancesApi;
    private final AccountLegsApi accountLegsApi;
    private final LedgerGlProperties ledgerGlProperties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public PostCardStatementPaymentSaga(CommandBus commandBus,
                                        CardPaymentsApi cardPaymentsApi,
                                        CardBalancesApi cardBalancesApi,
                                        AccountLegsApi accountLegsApi,
                                        LedgerGlProperties ledgerGlProperties) {
        this.commandBus = commandBus;
        this.cardPaymentsApi = cardPaymentsApi;
        this.cardBalancesApi = cardBalancesApi;
        this.accountLegsApi = accountLegsApi;
        this.ledgerGlProperties = ledgerGlProperties;
    }

    @SagaStep(id = STEP_CREATE_CARD_PAYMENT, compensate = COMPENSATE_FAIL_PAYMENT)
    @StepEvent(type = EVENT_CARD_PAYMENT_CREATED)
    public Mono<UUID> createCardPayment(PostCardStatementPaymentCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_FUNDING_ACCOUNT_ID, cmd.getFundingAccountId());
        ctx.putVariable(CTX_STATEMENT_ID, cmd.getStatementId());
        ctx.putVariable(CTX_PAYMENT_AMOUNT, cmd.getPaymentAmount());
        ctx.putVariable(CTX_CURRENCY, cmd.getCurrency());
        ctx.putVariable(CTX_IS_AUTO, cmd.getIsAutoPayment());
        ctx.putVariable(CTX_IS_FULL, cmd.getIsFullPayment());
        ctx.putVariable(CTX_PAYMENT_REFERENCE, cmd.getPaymentReference());

        CardPaymentDTO dto = new CardPaymentDTO()
                .cardId(cmd.getCardId())
                .accountId(cmd.getFundingAccountId())
                .statementId(cmd.getStatementId())
                .paymentReference(cmd.getPaymentReference())
                .paymentAmount(cmd.getPaymentAmount())
                .currencyCode(cmd.getCurrency())
                .paymentStatus(PAYMENT_STATUS_PENDING)
                .paymentTimestamp(LocalDateTime.now())
                .isAutoPayment(cmd.getIsAutoPayment())
                .isFullPayment(cmd.getIsFullPayment());

        return cardPaymentsApi.createPayment(cmd.getCardId(), dto, cmd.getPaymentReference())
                .map(CardPaymentDTO::getPaymentId)
                .doOnNext(id -> ctx.putVariable(CTX_CARD_PAYMENT_ID, id));
    }

    /** Compensation — marks the payment FAILED when a downstream step fails. */
    public Mono<Void> failCardPayment(UUID ignoredResult, ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        UUID cardPaymentId = (UUID) ctx.getVariable(CTX_CARD_PAYMENT_ID);
        if (cardId == null || cardPaymentId == null) {
            return Mono.empty();
        }
        CardPaymentDTO patch = new CardPaymentDTO().paymentStatus(PAYMENT_STATUS_FAILED);
        return cardPaymentsApi.updatePayment(cardId, cardPaymentId, patch, UUID.randomUUID().toString()).then();
    }

    @SagaStep(id = STEP_POST_PAYMENT_LEDGER,
            compensate = COMPENSATE_REVERSE_PAYMENT_LEDGER,
            dependsOn = STEP_CREATE_CARD_PAYMENT)
    @StepEvent(type = EVENT_PAYMENT_LEDGER_POSTED)
    public Mono<UUID> postPaymentLedger(PostCardStatementPaymentCommand ignoredCmd,
                                        @CorrelationId String sagaId,
                                        ExecutionContext ctx) {
        UUID fundingAccountId = ctx.getVariableAs(CTX_FUNDING_ACCOUNT_ID, UUID.class);
        BigDecimal amount = ctx.getVariableAs(CTX_PAYMENT_AMOUNT, BigDecimal.class);
        String currency = ctx.getVariableAs(CTX_CURRENCY, String.class);
        String paymentReference = ctx.getVariableAs(CTX_PAYMENT_REFERENCE, String.class);
        UUID receivableGl = ledgerGlProperties.getCreditCardReceivableAccountId();

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("card-statement-payment:"
                        + (paymentReference == null ? sagaId + ":" + STEP_POST_PAYMENT_LEDGER : paymentReference))
                .transactionType(TX_TYPE_TRANSFER)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_PAYMENT)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(fundingAccountId).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(receivableGl).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .lineType(LINE_TYPE_TRANSFER)
                .lineDto(new TransactionLineTransferDTO()
                        .transferPurpose(TRANSFER_PURPOSE_STATEMENT_PAYMENT)
                        .transferReference(paymentReference))
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(cmd)
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_PAYMENT_LEDGER_TX_ID, id));
    }

    /**
     * Compensation for {@link #postPaymentLedger} — posts a REVERSAL transaction that inverts
     * the TRANSFER legs so the funding account and receivable GL are restored.
     */
    public Mono<Void> reversePaymentLedger(UUID ignoredResult, @CorrelationId String sagaId, ExecutionContext ctx) {
        UUID paymentLedgerTxId = (UUID) ctx.getVariable(CTX_PAYMENT_LEDGER_TX_ID);
        if (paymentLedgerTxId == null) {
            return Mono.empty();
        }
        UUID fundingAccountId = ctx.getVariableAs(CTX_FUNDING_ACCOUNT_ID, UUID.class);
        BigDecimal amount = ctx.getVariableAs(CTX_PAYMENT_AMOUNT, BigDecimal.class);
        String currency = ctx.getVariableAs(CTX_CURRENCY, String.class);
        UUID receivableGl = ledgerGlProperties.getCreditCardReceivableAccountId();

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand reversal = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + COMPENSATE_REVERSE_PAYMENT_LEDGER)
                .transactionType(TX_TYPE_TRANSFER)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_PAYMENT_REVERSAL)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(receivableGl).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(fundingAccountId).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .relatedTransactionId(paymentLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();
        return commandBus.<PostLedgerTransactionResult>send(reversal).then();
    }

    @SagaStep(id = STEP_UPDATE_CARD_PAYMENT, dependsOn = STEP_POST_PAYMENT_LEDGER)
    @StepEvent(type = EVENT_CARD_PAYMENT_UPDATED)
    public Mono<UUID> updateCardPayment(PostCardStatementPaymentCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID cardPaymentId = ctx.getVariableAs(CTX_CARD_PAYMENT_ID, UUID.class);
        CardPaymentDTO patch = new CardPaymentDTO()
                .paymentStatus(PAYMENT_STATUS_COMPLETED)
                .postingTimestamp(LocalDateTime.now());
        return cardPaymentsApi.updatePayment(cardId, cardPaymentId, patch, UUID.randomUUID().toString())
                .thenReturn(cardPaymentId);
    }

    @SagaStep(id = STEP_UPDATE_STATEMENT, dependsOn = STEP_UPDATE_CARD_PAYMENT)
    @StepEvent(type = EVENT_STATEMENT_UPDATED)
    public Mono<Object> updateStatement(PostCardStatementPaymentCommand ignoredCmd, ExecutionContext ctx) {
        UUID statementId = (UUID) ctx.getVariable(CTX_STATEMENT_ID);
        log.info("Statement update skipped — core-banking-cards SDK does not expose a statements mutation API; "
                + "statementId={}", statementId);
        return Mono.just(RESULT_SKIPPED);
    }

    @SagaStep(id = STEP_REFRESH_CARD_BALANCE, dependsOn = STEP_UPDATE_STATEMENT)
    @StepEvent(type = EVENT_CARD_BALANCE_REFRESHED)
    public Mono<Object> refreshCardBalance(PostCardStatementPaymentCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID fundingAccountId = ctx.getVariableAs(CTX_FUNDING_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_CURRENCY, String.class);
        if (fundingAccountId == null) {
            return Mono.just(RESULT_SKIPPED);
        }
        return accountLegsApi.listAccountLegs(fundingAccountId, 0, 1000, null, null, UUID.randomUUID().toString())
                .flatMapMany(this::toLegFlux)
                .reduce(BigDecimal.ZERO, (acc, leg) -> {
                    BigDecimal amount = leg.getAmount() == null ? BigDecimal.ZERO : leg.getAmount();
                    boolean debit = LEG_DEBIT.equalsIgnoreCase(leg.getLegType());
                    return acc.add(debit ? amount : amount.negate());
                })
                .flatMap(balance -> upsertLedgerBalance(cardId, currency, balance))
                .thenReturn((Object) RESULT_SKIPPED)
                .defaultIfEmpty(RESULT_SKIPPED);
    }

    private Flux<TransactionLegDTO> toLegFlux(PaginationResponse page) {
        Object content = page == null ? null : page.getContent();
        if (!(content instanceof List<?> list)) {
            return Flux.empty();
        }
        return Flux.fromIterable(list).map(entry -> objectMapper.convertValue(entry, TransactionLegDTO.class));
    }

    private Mono<CardBalanceDTO> upsertLedgerBalance(UUID cardId, String currency, BigDecimal balance) {
        return cardBalancesApi.getAllBalances(cardId, null, null, null, null, UUID.randomUUID().toString())
                .flatMap(this::findLedgerBalance)
                .flatMap(existing -> {
                    CardBalanceDTO patch = new CardBalanceDTO()
                            .balanceAmount(balance)
                            .asOfDate(LocalDateTime.now());
                    return cardBalancesApi.updateBalance(cardId, existing.getBalanceId(), patch, UUID.randomUUID().toString());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CardBalanceDTO dto = new CardBalanceDTO()
                            .cardId(cardId)
                            .balanceType(BALANCE_TYPE_LEDGER)
                            .balanceAmount(balance)
                            .currencyCode(currency)
                            .asOfDate(LocalDateTime.now());
                    return cardBalancesApi.createBalance(cardId, dto, UUID.randomUUID().toString());
                }));
    }

    private Mono<CardBalanceDTO> findLedgerBalance(com.firefly.core.banking.cards.sdk.model.PaginationResponse page) {
        Object content = page == null ? null : page.getContent();
        if (!(content instanceof List<?> list)) {
            return Mono.empty();
        }
        return Flux.fromIterable(list)
                .filter(entry -> entry instanceof CardBalanceDTO)
                .map(entry -> (CardBalanceDTO) entry)
                .filter(b -> BALANCE_TYPE_LEDGER.equalsIgnoreCase(b.getBalanceType()))
                .next();
    }

}
