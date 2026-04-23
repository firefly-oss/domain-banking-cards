package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.cards.sdk.model.CardTransactionDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.core.banking.ledger.sdk.model.PaginationResponse;
import com.firefly.core.banking.ledger.sdk.model.TransactionDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLegDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineCardDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.ClearCardTransactionCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CLEARING_PATH_REVERSAL_PLUS_NEW;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CLEARING_PATH_TRANSITION;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.COMPENSATE_RESTORE_CARD_TX;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.COMPENSATE_REVERSE_CLEARING;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_AUTHORIZED_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_AUTH_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CLEARED_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CLEARING_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CLEARING_PATH;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_EXTERNAL_AUTH_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_NETWORK_CLEARING_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_SETTLEMENT_TIMESTAMP;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.EVENT_AUTH_LOOKED_UP;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.EVENT_CARD_BALANCE_REFRESHED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.EVENT_CARD_TX_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.EVENT_CLEARING_POSTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.SAGA_CLEAR_CARD_TX_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_LOOKUP_AUTH;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_POST_CLEARING_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_REFRESH_CARD_BALANCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_UPDATE_CARD_TX;

/**
 * Saga orchestrator for clearing a previously-authorized card transaction:
 *
 * <ol>
 *   <li>{@code STEP_LOOKUP_AUTH} — reads the {@code card_transaction} and locates the original
 *       PENDING ledger transaction via {@link TransactionsApi#findByExternalReference}. Fails
 *       the saga when the authorization cannot be located.</li>
 *   <li>{@code STEP_POST_CLEARING_LEDGER} — transitions the original PENDING tx to POSTED
 *       when cleared amount matches the authorized amount; otherwise posts a REVERSAL of the
 *       authorization and a new POSTED transaction for the cleared amount.</li>
 *   <li>{@code STEP_UPDATE_CARD_TX} — sets {@code card_transaction.transactionStatus = COMPLETED}.
 *       The settlement timestamp is stored as the {@code cardTransactionTimestamp} because the
 *       SDK does not expose a dedicated settlement-timestamp field.</li>
 *   <li>{@code STEP_REFRESH_CARD_BALANCE} — recomputes {@code ledgerBalance} and
 *       {@code pendingAmount} from ledger legs and persists a snapshot projection.</li>
 * </ol>
 *
 * <p>Compensation reverses the clearing ledger posts and restores the card transaction to
 * PENDING. Every compensator is null-safe against interrupted steps.
 */
@Slf4j
@Saga(name = SAGA_CLEAR_CARD_TX_NAME)
@Service
public class ClearCardTransactionSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_CARD = "CARD";
    private static final String LINE_TYPE_CARD = "CARD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String RELATION_ADJUSTMENT = "ADJUSTMENT";
    private static final String DESC_CLEARED = "Card clearing — transitioned to POSTED";
    private static final String DESC_AUTH_REVERSAL = "Card clearing — REVERSAL of authorization amount";
    private static final String DESC_NEW_CLEARING = "Card clearing — new POSTED for cleared amount";
    private static final String DESC_CLEARING_ROLLBACK = "Compensation — rollback of clearing ledger post";
    private static final String BALANCE_TYPE_LEDGER = "LEDGER";

    private final CommandBus commandBus;
    private final CardsApi cardsApi;
    private final CardTransactionsApi cardTransactionsApi;
    private final CardBalancesApi cardBalancesApi;
    private final TransactionsApi transactionsApi;
    private final AccountLegsApi accountLegsApi;
    private final LedgerGlProperties ledgerGlProperties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ClearCardTransactionSaga(CommandBus commandBus,
                                    CardsApi cardsApi,
                                    CardTransactionsApi cardTransactionsApi,
                                    CardBalancesApi cardBalancesApi,
                                    TransactionsApi transactionsApi,
                                    AccountLegsApi accountLegsApi,
                                    LedgerGlProperties ledgerGlProperties) {
        this.commandBus = commandBus;
        this.cardsApi = cardsApi;
        this.cardTransactionsApi = cardTransactionsApi;
        this.cardBalancesApi = cardBalancesApi;
        this.transactionsApi = transactionsApi;
        this.accountLegsApi = accountLegsApi;
        this.ledgerGlProperties = ledgerGlProperties;
    }

    @SagaStep(id = STEP_LOOKUP_AUTH)
    @StepEvent(type = EVENT_AUTH_LOOKED_UP)
    public Mono<UUID> lookupAuthorization(ClearCardTransactionCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_CARD_TRANSACTION_ID, cmd.getCardTransactionId());
        ctx.putVariable(CTX_CLEARED_AMOUNT, cmd.getClearedAmount());
        ctx.putVariable(CTX_SETTLEMENT_TIMESTAMP, cmd.getSettlementTimestamp());
        ctx.putVariable(CTX_NETWORK_CLEARING_REFERENCE, cmd.getNetworkClearingReference());

        return cardTransactionsApi
                .getTransaction(cmd.getCardId(), cmd.getCardTransactionId(), UUID.randomUUID().toString())
                .flatMap(cardTx -> {
                    String externalAuthReference = cardTx.getCardTransactionReference();
                    ctx.putVariable(CTX_EXTERNAL_AUTH_REFERENCE, externalAuthReference);

                    return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                            .flatMap(card -> enrichWithAccountContext(card, ctx))
                            .then(findAuthLedgerTx(externalAuthReference))
                            .flatMap(authTx -> {
                                UUID authLedgerTxId = authTx.getTransactionId();
                                BigDecimal resolvedAuthAmount = authTx.getTotalAmount();
                                ctx.putVariable(CTX_AUTH_LEDGER_TX_ID, authLedgerTxId);
                                ctx.putVariable(CTX_AUTHORIZED_AMOUNT, resolvedAuthAmount);
                                return Mono.just(authLedgerTxId);
                            });
                })
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "unable to locate authorization ledger tx for cardTransactionId=" + cmd.getCardTransactionId())));
    }

    private Mono<Void> enrichWithAccountContext(CardDTO card, ExecutionContext ctx) {
        if (card.getAccountId() == null) {
            return Mono.error(new IllegalStateException("card has no linked accountId"));
        }
        ctx.putVariable(CTX_ACCOUNT_ID, card.getAccountId());
        ctx.putVariable(CTX_ACCOUNT_CURRENCY, card.getCurrencyCode());
        return Mono.empty();
    }

    private Mono<TransactionDTO> findAuthLedgerTx(String externalAuthReference) {
        if (externalAuthReference == null || externalAuthReference.isBlank()) {
            return Mono.empty();
        }
        return transactionsApi.findByExternalReference(
                "card-auth:" + externalAuthReference, UUID.randomUUID().toString());
    }

    @SagaStep(id = STEP_POST_CLEARING_LEDGER, compensate = COMPENSATE_REVERSE_CLEARING, dependsOn = STEP_LOOKUP_AUTH)
    @StepEvent(type = EVENT_CLEARING_POSTED)
    public Mono<UUID> postClearingLedger(ClearCardTransactionCommand ignoredCmd,
                                         @CorrelationId String sagaId,
                                         ExecutionContext ctx) {
        UUID authLedgerTxId = ctx.getVariableAs(CTX_AUTH_LEDGER_TX_ID, UUID.class);
        BigDecimal authorizedAmount = ctx.getVariableAs(CTX_AUTHORIZED_AMOUNT, BigDecimal.class);
        BigDecimal clearedAmount = ctx.getVariableAs(CTX_CLEARED_AMOUNT, BigDecimal.class);
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String accountCurrency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        UUID glAccountId = ledgerGlProperties.getCardAuthSuspenseAccountId();
        LocalDateTime settlement = ctx.getVariableAs(CTX_SETTLEMENT_TIMESTAMP, LocalDateTime.class);

        if (authorizedAmount != null && clearedAmount != null && authorizedAmount.compareTo(clearedAmount) == 0) {
            ctx.putVariable(CTX_CLEARING_PATH, CLEARING_PATH_TRANSITION);
            return transactionsApi.updateTransactionStatus(
                            authLedgerTxId,
                            STATUS_POSTED,
                            DESC_CLEARED + (settlement == null ? "" : " @ " + settlement),
                            UUID.randomUUID().toString())
                    .map(TransactionDTO::getTransactionId)
                    .doOnNext(id -> ctx.putVariable(CTX_CLEARING_LEDGER_TX_ID, id))
                    .doOnSuccess(id -> log.info(
                            "Transitioned auth tx to POSTED sagaId={} ledgerTxId={}", sagaId, id));
        }

        ctx.putVariable(CTX_CLEARING_PATH, CLEARING_PATH_REVERSAL_PLUS_NEW);
        PostLedgerTransactionCommand reversal = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + STEP_POST_CLEARING_LEDGER + ":REVERSAL")
                .transactionType(TX_TYPE_CARD)
                .totalAmount(authorizedAmount)
                .currency(accountCurrency)
                .valueDate(LocalDateTime.now())
                .bookingDate(LocalDateTime.now())
                .description(DESC_AUTH_REVERSAL)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(glAccountId).legType(LEG_DEBIT).amount(authorizedAmount).currency(accountCurrency).build(),
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(authorizedAmount).currency(accountCurrency).build()))
                .relatedTransactionId(authLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();

        PostLedgerTransactionCommand newPost = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + STEP_POST_CLEARING_LEDGER + ":CLEARING")
                .transactionType(TX_TYPE_CARD)
                .totalAmount(clearedAmount)
                .currency(accountCurrency)
                .valueDate(settlement == null ? LocalDateTime.now() : settlement)
                .bookingDate(settlement == null ? LocalDateTime.now() : settlement)
                .description(DESC_NEW_CLEARING)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_DEBIT).amount(clearedAmount).currency(accountCurrency).build(),
                        LedgerLegSpec.builder().accountId(glAccountId).legType(LEG_CREDIT).amount(clearedAmount).currency(accountCurrency).build()))
                .lineType(LINE_TYPE_CARD)
                .lineDto(new TransactionLineCardDTO()
                        .cardTransactionReference(ctx.getVariableAs(CTX_NETWORK_CLEARING_REFERENCE, String.class))
                        .cardTransactionTimestamp(settlement))
                .relatedTransactionId(authLedgerTxId)
                .relationType(RELATION_ADJUSTMENT)
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(reversal)
                .then(commandBus.<PostLedgerTransactionResult>send(newPost))
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_CLEARING_LEDGER_TX_ID, id))
                .doOnSuccess(id -> log.info("Posted REVERSAL+CLEARING path sagaId={} newLedgerTxId={}", sagaId, id));
    }

    /**
     * Compensation for {@link #postClearingLedger} — reverses the ledger post(s) made during
     * clearing so the authorization is restored to PENDING semantics. Null-safe against
     * interrupted steps.
     */
    public Mono<Void> reverseClearingLedger(UUID ignoredResult, @CorrelationId String sagaId, ExecutionContext ctx) {
        UUID clearingLedgerTxId = (UUID) ctx.getVariable(CTX_CLEARING_LEDGER_TX_ID);
        String path = (String) ctx.getVariable(CTX_CLEARING_PATH);
        if (clearingLedgerTxId == null || path == null) {
            return Mono.empty();
        }

        if (CLEARING_PATH_TRANSITION.equals(path)) {
            return transactionsApi.updateTransactionStatus(
                            clearingLedgerTxId,
                            STATUS_PENDING,
                            DESC_CLEARING_ROLLBACK,
                            UUID.randomUUID().toString())
                    .doOnSuccess(tx -> log.info("Rolled back clearing transition sagaId={} ledgerTxId={}",
                            sagaId, clearingLedgerTxId))
                    .then();
        }

        BigDecimal clearedAmount = ctx.getVariableAs(CTX_CLEARED_AMOUNT, BigDecimal.class);
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String accountCurrency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        UUID glAccountId = ledgerGlProperties.getCardAuthSuspenseAccountId();
        if (clearedAmount == null || accountId == null || accountCurrency == null || glAccountId == null) {
            return Mono.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand rollback = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + COMPENSATE_REVERSE_CLEARING)
                .transactionType(TX_TYPE_CARD)
                .totalAmount(clearedAmount)
                .currency(accountCurrency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_CLEARING_ROLLBACK)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(glAccountId).legType(LEG_DEBIT).amount(clearedAmount).currency(accountCurrency).build(),
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(clearedAmount).currency(accountCurrency).build()))
                .relatedTransactionId(clearingLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();
        return commandBus.<PostLedgerTransactionResult>send(rollback).then();
    }

    @SagaStep(id = STEP_UPDATE_CARD_TX, compensate = COMPENSATE_RESTORE_CARD_TX, dependsOn = STEP_POST_CLEARING_LEDGER)
    @StepEvent(type = EVENT_CARD_TX_UPDATED)
    public Mono<UUID> updateCardTransaction(ClearCardTransactionCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID cardTransactionId = ctx.getVariableAs(CTX_CARD_TRANSACTION_ID, UUID.class);
        LocalDateTime settlement = ctx.getVariableAs(CTX_SETTLEMENT_TIMESTAMP, LocalDateTime.class);

        CardTransactionDTO patch = new CardTransactionDTO()
                .transactionStatus(CardTransactionDTO.TransactionStatusEnum.COMPLETED)
                .cardTransactionTimestamp(settlement);
        return cardTransactionsApi.updateTransaction(cardId, cardTransactionId, patch, UUID.randomUUID().toString())
                .thenReturn(cardTransactionId);
    }

    /**
     * Compensation for {@link #updateCardTransaction} — restores the card_transaction to
     * PENDING. Null-safe when the step never ran.
     */
    public Mono<Void> restoreCardTransaction(UUID ignoredResult, ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        UUID cardTransactionId = (UUID) ctx.getVariable(CTX_CARD_TRANSACTION_ID);
        if (cardId == null || cardTransactionId == null) {
            return Mono.empty();
        }
        CardTransactionDTO patch = new CardTransactionDTO()
                .transactionStatus(CardTransactionDTO.TransactionStatusEnum.PENDING);
        return cardTransactionsApi.updateTransaction(cardId, cardTransactionId, patch, UUID.randomUUID().toString())
                .then();
    }

    @SagaStep(id = STEP_REFRESH_CARD_BALANCE, dependsOn = STEP_UPDATE_CARD_TX)
    @StepEvent(type = EVENT_CARD_BALANCE_REFRESHED)
    public Mono<Object> refreshCardBalance(ClearCardTransactionCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        if (accountId == null) {
            return Mono.just(RESULT_SKIPPED);
        }
        return computeBalanceFromLegs(accountId)
                .flatMap(amounts -> upsertLedgerBalance(cardId, currency, amounts))
                .thenReturn((Object) RESULT_SKIPPED)
                .defaultIfEmpty(RESULT_SKIPPED);
    }

    private Mono<LedgerAmounts> computeBalanceFromLegs(UUID accountId) {
        return accountLegsApi.listAccountLegs(accountId, 0, 1000, null, null, UUID.randomUUID().toString())
                .flatMapMany(this::toLegFlux)
                .reduce(new LedgerAmounts(), (acc, leg) -> {
                    BigDecimal amount = leg.getAmount() == null ? BigDecimal.ZERO : leg.getAmount();
                    boolean debit = LEG_DEBIT.equalsIgnoreCase(leg.getLegType());
                    BigDecimal signed = debit ? amount : amount.negate();
                    acc.ledgerBalance = acc.ledgerBalance.add(signed);
                    return acc;
                });
    }

    private Flux<TransactionLegDTO> toLegFlux(PaginationResponse page) {
        Object content = page == null ? null : page.getContent();
        if (!(content instanceof List<?> list)) {
            return Flux.empty();
        }
        return Flux.fromIterable(list)
                .map(entry -> objectMapper.convertValue(entry, TransactionLegDTO.class));
    }

    private Mono<CardBalanceDTO> upsertLedgerBalance(UUID cardId, String currency, LedgerAmounts amounts) {
        return cardBalancesApi.getAllBalances(cardId, null, null, null, null, UUID.randomUUID().toString())
                .flatMap(page -> findLedgerBalance(page))
                .flatMap(existing -> {
                    CardBalanceDTO patch = new CardBalanceDTO()
                            .balanceAmount(amounts.ledgerBalance)
                            .asOfDate(LocalDateTime.now());
                    return cardBalancesApi.updateBalance(cardId, existing.getBalanceId(), patch, UUID.randomUUID().toString());
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CardBalanceDTO dto = new CardBalanceDTO()
                            .cardId(cardId)
                            .balanceType(BALANCE_TYPE_LEDGER)
                            .balanceAmount(amounts.ledgerBalance)
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

    private static final class LedgerAmounts {
        private BigDecimal ledgerBalance = BigDecimal.ZERO;
    }
}
