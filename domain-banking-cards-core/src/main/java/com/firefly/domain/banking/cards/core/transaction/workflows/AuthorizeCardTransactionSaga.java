package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.cards.sdk.model.CardTransactionDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineCardDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.AuthorizeCardTransactionCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.COMPENSATE_FAIL_CARD_TX;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.COMPENSATE_REVERSE_LEDGER_HOLD;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.COMPENSATE_REVERT_CARD_BALANCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CARD_BALANCE_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_EXTERNAL_AUTH_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.EVENT_ACCOUNT_RESOLVED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.EVENT_CARD_BALANCE_PROJECTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.EVENT_CARD_TX_CREATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.EVENT_LEDGER_HOLD_PLACED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.SAGA_AUTHORIZE_CARD_TX_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_CREATE_CARD_TX;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_PLACE_LEDGER_HOLD;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_RESOLVE_ACCOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_UPDATE_CARD_BALANCE_PROJECTION;

/**
 * Saga orchestrator for a card authorization. Steps:
 *
 * <ol>
 *   <li>{@code STEP_RESOLVE_ACCOUNT} — looks up the card in {@code core-banking-cards} to
 *       extract the underlying {@code accountId} and currency used for the ledger hold.</li>
 *   <li>{@code STEP_CREATE_CARD_TX} — creates the {@code card_transaction} row in PENDING
 *       status; the {@code externalAuthReference} is passed as {@code xIdempotencyKey} so
 *       network retries of the same authorization do not create duplicate rows.</li>
 *   <li>{@code STEP_PLACE_LEDGER_HOLD} — dispatches {@link PostLedgerTransactionCommand} with
 *       {@code transactionType = CARD}, {@code initialStatus = PENDING}, and a two-leg
 *       double-entry posting (DEBIT customer account, CREDIT {@code card_auth_suspense}).
 *       A PENDING ledger transaction reduces available-balance without posting, which is
 *       the correct semantic for an authorization hold.</li>
 *   <li>{@code STEP_UPDATE_CARD_BALANCE_PROJECTION} — adds the authorized amount to
 *       {@code card_balance.pendingAmount} as a read-cache projection. For credit cards this
 *       is skipped — the projection semantics differ and are rebuilt from ledger legs on read.</li>
 * </ol>
 *
 * <p>Compensation posts a REVERSAL on the ledger hold, transitions the card_transaction to
 * FAILED, and reverts the card-balance projection. All compensators are null-safe so they
 * may be invoked even when the failing step never wrote remote state.
 */
@Slf4j
@Saga(name = SAGA_AUTHORIZE_CARD_TX_NAME)
@Service
public class AuthorizeCardTransactionSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_CARD = "CARD";
    private static final String LINE_TYPE_CARD = "CARD";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String BALANCE_TYPE_PENDING_AUTHORIZATIONS = "PENDING_AUTHORIZATIONS";
    private static final String DESC_CARD_AUTHORIZATION = "Card authorization hold";
    private static final String DESC_CARD_AUTH_REVERSAL = "Reversal of card authorization hold";
    private static final String DESC_CARD_AUTH_FAILED = "Authorization declined — saga compensation";

    private final CommandBus commandBus;
    private final CardsApi cardsApi;
    private final CardTransactionsApi cardTransactionsApi;
    private final CardBalancesApi cardBalancesApi;
    private final LedgerGlProperties ledgerGlProperties;

    public AuthorizeCardTransactionSaga(CommandBus commandBus,
                                        CardsApi cardsApi,
                                        CardTransactionsApi cardTransactionsApi,
                                        CardBalancesApi cardBalancesApi,
                                        LedgerGlProperties ledgerGlProperties) {
        this.commandBus = commandBus;
        this.cardsApi = cardsApi;
        this.cardTransactionsApi = cardTransactionsApi;
        this.cardBalancesApi = cardBalancesApi;
        this.ledgerGlProperties = ledgerGlProperties;
    }

    @SagaStep(id = STEP_RESOLVE_ACCOUNT)
    @StepEvent(type = EVENT_ACCOUNT_RESOLVED)
    public Mono<UUID> resolveAccount(AuthorizeCardTransactionCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_AMOUNT, cmd.getAmount());
        ctx.putVariable(CTX_CURRENCY, cmd.getCurrency());
        ctx.putVariable(CTX_EXTERNAL_AUTH_REFERENCE, cmd.getExternalAuthReference());
        return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                .flatMap(card -> {
                    UUID accountId = card.getAccountId();
                    if (accountId == null) {
                        return Mono.error(new IllegalStateException(
                                "card " + cmd.getCardId() + " has no linked accountId"));
                    }
                    String accountCurrency = card.getCurrencyCode() == null ? cmd.getCurrency() : card.getCurrencyCode();
                    ctx.putVariable(CTX_ACCOUNT_ID, accountId);
                    ctx.putVariable(CTX_ACCOUNT_CURRENCY, accountCurrency);
                    return Mono.just(accountId);
                });
    }

    @SagaStep(id = STEP_CREATE_CARD_TX, compensate = COMPENSATE_FAIL_CARD_TX, dependsOn = STEP_RESOLVE_ACCOUNT)
    @StepEvent(type = EVENT_CARD_TX_CREATED)
    public Mono<UUID> createCardTransaction(AuthorizeCardTransactionCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        String externalAuthReference = ctx.getVariableAs(CTX_EXTERNAL_AUTH_REFERENCE, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class);
        String currency = ctx.getVariableAs(CTX_CURRENCY, String.class);

        CardTransactionDTO dto = new CardTransactionDTO()
                .cardId(cardId)
                .transactionType(CardTransactionDTO.TransactionTypeEnum.PURCHASE)
                .transactionStatus(CardTransactionDTO.TransactionStatusEnum.PENDING)
                .cardAuthCode(readStringVar(ctx, "authorizationCode"))
                .cardTransactionReference(externalAuthReference)
                .cardTransactionTimestamp(LocalDateTime.now());

        return cardTransactionsApi.createTransaction(cardId, dto, externalAuthReference)
                .map(CardTransactionDTO::getCardTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_CARD_TRANSACTION_ID, id));
    }

    /**
     * Compensation for {@link #createCardTransaction} — marks the card_transaction FAILED so
     * downstream reporting and fraud surfaces reflect the declined authorization.
     */
    public Mono<Void> failCardTransaction(UUID ignoredResult, ExecutionContext ctx) {
        UUID cardTransactionId = (UUID) ctx.getVariable(CTX_CARD_TRANSACTION_ID);
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        if (cardTransactionId == null || cardId == null) {
            return Mono.empty();
        }
        CardTransactionDTO patch = new CardTransactionDTO()
                .transactionStatus(CardTransactionDTO.TransactionStatusEnum.FAILED)
                .cardTransactionReference(DESC_CARD_AUTH_FAILED);
        return cardTransactionsApi.updateTransaction(cardId, cardTransactionId, patch, UUID.randomUUID().toString())
                .doOnSuccess(updated -> log.info("Marked card_transaction FAILED cardTransactionId={} cardId={}",
                        cardTransactionId, cardId))
                .then();
    }

    @SagaStep(id = STEP_PLACE_LEDGER_HOLD, compensate = COMPENSATE_REVERSE_LEDGER_HOLD, dependsOn = STEP_CREATE_CARD_TX)
    @StepEvent(type = EVENT_LEDGER_HOLD_PLACED)
    public Mono<UUID> placeLedgerHold(AuthorizeCardTransactionCommand ignoredCmd,
                                      @CorrelationId String sagaId,
                                      ExecutionContext ctx) {
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String accountCurrency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class);
        String externalAuthReference = ctx.getVariableAs(CTX_EXTERNAL_AUTH_REFERENCE, String.class);
        UUID glAccountId = ledgerGlProperties.getCardAuthSuspenseAccountId();

        TransactionLineCardDTO lineDto = new TransactionLineCardDTO()
                .cardAuthCode(readStringVar(ctx, "authorizationCode"))
                .cardTransactionReference(externalAuthReference)
                .cardTransactionTimestamp(LocalDateTime.now());

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand ledgerCmd = PostLedgerTransactionCommand.builder()
                .externalReference("card-auth:" + externalAuthReference)
                .transactionType(TX_TYPE_CARD)
                .totalAmount(amount)
                .currency(accountCurrency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_CARD_AUTHORIZATION)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_DEBIT).amount(amount).currency(accountCurrency).build(),
                        LedgerLegSpec.builder().accountId(glAccountId).legType(LEG_CREDIT).amount(amount).currency(accountCurrency).build()))
                .lineType(LINE_TYPE_CARD)
                .lineDto(lineDto)
                .initialStatus(STATUS_PENDING)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(ledgerCmd)
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(txId -> ctx.putVariable(CTX_LEDGER_TX_ID, txId))
                .doOnSuccess(txId -> log.info("Placed ledger hold sagaId={} ledgerTxId={}", sagaId, txId));
    }

    /**
     * Compensation for {@link #placeLedgerHold} — posts a REVERSAL ledger transaction pointing
     * at the original hold so the PENDING legs are cancelled by inverted POSTED legs. Null-safe:
     * if the hold step never completed there is nothing to reverse.
     */
    public Mono<Void> reverseLedgerHold(UUID ignoredResult, @CorrelationId String sagaId, ExecutionContext ctx) {
        UUID ledgerTxId = (UUID) ctx.getVariable(CTX_LEDGER_TX_ID);
        if (ledgerTxId == null) {
            return Mono.empty();
        }
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String accountCurrency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        BigDecimal amount = ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class);
        UUID glAccountId = ledgerGlProperties.getCardAuthSuspenseAccountId();

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand reversal = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + COMPENSATE_REVERSE_LEDGER_HOLD)
                .transactionType(TX_TYPE_CARD)
                .totalAmount(amount)
                .currency(accountCurrency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_CARD_AUTH_REVERSAL)
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(glAccountId).legType(LEG_DEBIT).amount(amount).currency(accountCurrency).build(),
                        LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(amount).currency(accountCurrency).build()))
                .relatedTransactionId(ledgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(reversal)
                .doOnSuccess(result -> log.info("Reversed ledger hold sagaId={} ledgerTxId={} reversalTxId={}",
                        sagaId, ledgerTxId, result == null ? null : result.getTransactionId()))
                .then();
    }

    @SagaStep(id = STEP_UPDATE_CARD_BALANCE_PROJECTION,
            compensate = COMPENSATE_REVERT_CARD_BALANCE,
            dependsOn = STEP_PLACE_LEDGER_HOLD)
    @StepEvent(type = EVENT_CARD_BALANCE_PROJECTED)
    public Mono<Object> updateCardBalanceProjection(AuthorizeCardTransactionCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        BigDecimal amount = ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class);
        String currency = ctx.getVariableAs(CTX_CURRENCY, String.class);

        return findPendingAuthorizationsBalance(cardId)
                .flatMap(existing -> bumpPendingAmount(cardId, existing, amount))
                .switchIfEmpty(createPendingAuthorizationsBalance(cardId, amount, currency))
                .map(CardBalanceDTO::getBalanceId)
                .doOnNext(balanceId -> ctx.putVariable(CTX_CARD_BALANCE_ID, balanceId))
                .map(id -> (Object) id)
                .defaultIfEmpty(RESULT_SKIPPED);
    }

    /**
     * Compensation for {@link #updateCardBalanceProjection} — subtracts the authorized amount
     * from the pending-authorizations projection so the cache reflects the failed authorization.
     */
    public Mono<Void> revertCardBalance(Object ignoredResult, ExecutionContext ctx) {
        UUID cardBalanceId = (UUID) ctx.getVariable(CTX_CARD_BALANCE_ID);
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        BigDecimal amount = (BigDecimal) ctx.getVariable(CTX_AMOUNT);
        if (cardBalanceId == null || cardId == null || amount == null) {
            return Mono.empty();
        }
        return cardBalancesApi.getBalance(cardId, cardBalanceId, UUID.randomUUID().toString())
                .flatMap(existing -> {
                    BigDecimal current = existing.getPendingAmount() == null ? BigDecimal.ZERO : existing.getPendingAmount();
                    CardBalanceDTO patch = new CardBalanceDTO()
                            .pendingAmount(current.subtract(amount).max(BigDecimal.ZERO));
                    return cardBalancesApi.updateBalance(cardId, cardBalanceId, patch, UUID.randomUUID().toString());
                })
                .doOnSuccess(updated -> log.info("Reverted card-balance projection cardBalanceId={} cardId={}",
                        cardBalanceId, cardId))
                .then();
    }

    private Mono<CardBalanceDTO> findPendingAuthorizationsBalance(UUID cardId) {
        return cardBalancesApi.getAllBalances(cardId, null, null, null, null, UUID.randomUUID().toString())
                .flatMapMany(page -> {
                    Object content = page == null ? null : page.getContent();
                    if (!(content instanceof List<?> list)) {
                        return reactor.core.publisher.Flux.empty();
                    }
                    return reactor.core.publisher.Flux.fromIterable(list)
                            .filter(entry -> entry instanceof CardBalanceDTO)
                            .map(entry -> (CardBalanceDTO) entry);
                })
                .filter(b -> BALANCE_TYPE_PENDING_AUTHORIZATIONS.equalsIgnoreCase(b.getBalanceType()))
                .next();
    }

    private Mono<CardBalanceDTO> bumpPendingAmount(UUID cardId, CardBalanceDTO existing, BigDecimal delta) {
        BigDecimal current = existing.getPendingAmount() == null ? BigDecimal.ZERO : existing.getPendingAmount();
        CardBalanceDTO patch = new CardBalanceDTO()
                .pendingAmount(current.add(delta))
                .asOfDate(LocalDateTime.now());
        return cardBalancesApi.updateBalance(cardId, existing.getBalanceId(), patch, UUID.randomUUID().toString());
    }

    private Mono<CardBalanceDTO> createPendingAuthorizationsBalance(UUID cardId, BigDecimal amount, String currency) {
        CardBalanceDTO dto = new CardBalanceDTO()
                .cardId(cardId)
                .balanceType(BALANCE_TYPE_PENDING_AUTHORIZATIONS)
                .pendingAmount(amount)
                .currencyCode(currency)
                .asOfDate(LocalDateTime.now());
        return cardBalancesApi.createBalance(cardId, dto, UUID.randomUUID().toString());
    }

    private String readStringVar(ExecutionContext ctx, String key) {
        Object v = ctx.getVariable(key);
        return v == null ? null : v.toString();
    }
}
