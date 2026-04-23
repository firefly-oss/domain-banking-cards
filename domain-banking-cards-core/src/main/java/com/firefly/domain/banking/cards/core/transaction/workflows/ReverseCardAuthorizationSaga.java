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
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.ReverseCardAuthorizationCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_AUTH_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REASON;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REVERSAL_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REVERSAL_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REVERSAL_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.EVENT_AUTH_LOOKED_UP;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.EVENT_CARD_BALANCE_REFRESHED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.EVENT_CARD_TX_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.EVENT_REVERSAL_POSTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.SAGA_REVERSE_CARD_AUTH_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_LOOKUP_AUTH;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_POST_REVERSAL_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_REFRESH_CARD_BALANCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_UPDATE_CARD_TX;

/**
 * Saga orchestrator for reversing a pending card authorization — void, expiration, or
 * customer cancellation before clearing. Steps:
 *
 * <ol>
 *   <li>{@code STEP_LOOKUP_AUTH} — resolves {@code card_transaction}, card account, and the
 *       original PENDING ledger transaction. Fails the saga if either cannot be located.</li>
 *   <li>{@code STEP_POST_REVERSAL_LEDGER} — posts a POSTED REVERSAL pointing at the PENDING
 *       authorization; the inverted legs cancel the earlier hold.</li>
 *   <li>{@code STEP_UPDATE_CARD_TX} — marks the {@code card_transaction} as REVERSED.</li>
 *   <li>{@code STEP_REFRESH_CARD_BALANCE} — recomputes the projected ledger balance.</li>
 * </ol>
 *
 * <p>Once a REVERSAL has been posted we do not attempt to "undo" it in compensation — the
 * last two steps have null compensators so that partial failure does not create a loop.
 */
@Slf4j
@Saga(name = SAGA_REVERSE_CARD_AUTH_NAME)
@Service
public class ReverseCardAuthorizationSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_CARD = "CARD";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String DESC_REVERSAL = "Card authorization reversal";
    private static final String BALANCE_TYPE_LEDGER = "LEDGER";

    private final CommandBus commandBus;
    private final CardsApi cardsApi;
    private final CardTransactionsApi cardTransactionsApi;
    private final CardBalancesApi cardBalancesApi;
    private final TransactionsApi transactionsApi;
    private final AccountLegsApi accountLegsApi;
    private final LedgerGlProperties ledgerGlProperties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ReverseCardAuthorizationSaga(CommandBus commandBus,
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
    public Mono<UUID> lookupAuthorization(ReverseCardAuthorizationCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_CARD_TRANSACTION_ID, cmd.getCardTransactionId());
        ctx.putVariable(CTX_REASON, cmd.getReason());
        ctx.putVariable(CTX_REVERSAL_REFERENCE, cmd.getReversalReference());

        return cardTransactionsApi
                .getTransaction(cmd.getCardId(), cmd.getCardTransactionId(), UUID.randomUUID().toString())
                .flatMap(cardTx -> {
                    String externalAuthReference = cardTx.getCardTransactionReference();
                    if (externalAuthReference == null) {
                        return Mono.error(new IllegalStateException(
                                "card_transaction " + cmd.getCardTransactionId() + " has no auth reference"));
                    }
                    return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                            .flatMap(card -> enrichWithAccountContext(card, ctx))
                            .then(transactionsApi.findByExternalReference(
                                    "card-auth:" + externalAuthReference, UUID.randomUUID().toString()))
                            .switchIfEmpty(Mono.error(new IllegalStateException(
                                    "authorization ledger tx not found for cardTransactionId="
                                            + cmd.getCardTransactionId())))
                            .map(authTx -> {
                                UUID authLedgerTxId = authTx.getTransactionId();
                                BigDecimal authAmount = authTx.getTotalAmount();
                                ctx.putVariable(CTX_AUTH_LEDGER_TX_ID, authLedgerTxId);
                                ctx.putVariable(CTX_REVERSAL_AMOUNT, authAmount);
                                return authLedgerTxId;
                            });
                });
    }

    private Mono<Void> enrichWithAccountContext(CardDTO card, ExecutionContext ctx) {
        if (card.getAccountId() == null) {
            return Mono.error(new IllegalStateException("card has no linked accountId"));
        }
        ctx.putVariable(CTX_ACCOUNT_ID, card.getAccountId());
        ctx.putVariable(CTX_ACCOUNT_CURRENCY, card.getCurrencyCode());
        return Mono.empty();
    }

    @SagaStep(id = STEP_POST_REVERSAL_LEDGER, dependsOn = STEP_LOOKUP_AUTH)
    @StepEvent(type = EVENT_REVERSAL_POSTED)
    public Mono<UUID> postReversalLedger(ReverseCardAuthorizationCommand ignoredCmd,
                                         @CorrelationId String sagaId,
                                         ExecutionContext ctx) {
        UUID authLedgerTxId = ctx.getVariableAs(CTX_AUTH_LEDGER_TX_ID, UUID.class);
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_REVERSAL_AMOUNT, BigDecimal.class);
        UUID glAccountId = ledgerGlProperties.getCardAuthSuspenseAccountId();
        String reversalReference = ctx.getVariableAs(CTX_REVERSAL_REFERENCE, String.class);

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("card-auth-reversal:"
                        + (reversalReference == null ? sagaId + ":" + STEP_POST_REVERSAL_LEDGER : reversalReference))
                .transactionType(TX_TYPE_CARD)
                .totalAmount(amount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_REVERSAL)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(glAccountId).legType(LEG_DEBIT).amount(amount).currency(currency).build(),
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(amount).currency(currency).build()))
                .relatedTransactionId(authLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(cmd)
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_REVERSAL_LEDGER_TX_ID, id))
                .doOnSuccess(id -> log.info("Posted reversal sagaId={} reversalTxId={} originalTxId={}",
                        sagaId, id, authLedgerTxId));
    }

    @SagaStep(id = STEP_UPDATE_CARD_TX, dependsOn = STEP_POST_REVERSAL_LEDGER)
    @StepEvent(type = EVENT_CARD_TX_UPDATED)
    public Mono<UUID> updateCardTransaction(ReverseCardAuthorizationCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID cardTransactionId = ctx.getVariableAs(CTX_CARD_TRANSACTION_ID, UUID.class);
        CardTransactionDTO patch = new CardTransactionDTO()
                .transactionStatus(CardTransactionDTO.TransactionStatusEnum.REVERSED);
        return cardTransactionsApi.updateTransaction(cardId, cardTransactionId, patch, UUID.randomUUID().toString())
                .thenReturn(cardTransactionId);
    }

    @SagaStep(id = STEP_REFRESH_CARD_BALANCE, dependsOn = STEP_UPDATE_CARD_TX)
    @StepEvent(type = EVENT_CARD_BALANCE_REFRESHED)
    public Mono<Object> refreshCardBalance(ReverseCardAuthorizationCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        if (accountId == null) {
            return Mono.just(RESULT_SKIPPED);
        }
        return accountLegsApi.listAccountLegs(accountId, 0, 1000, null, null, UUID.randomUUID().toString())
                .flatMapMany(this::toLegFlux)
                .reduce(BigDecimal.ZERO, (acc, leg) -> {
                    BigDecimal amount = leg.getAmount() == null ? BigDecimal.ZERO : leg.getAmount();
                    boolean debit = LEG_DEBIT.equalsIgnoreCase(leg.getLegType());
                    return acc.add(debit ? amount : amount.negate());
                })
                .flatMap(ledgerBalance -> upsertLedgerBalance(cardId, currency, ledgerBalance))
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
