package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardDisputesApi;
import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.core.banking.cards.sdk.model.CardDisputeDTO;
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
import com.firefly.domain.banking.cards.core.transaction.commands.ResolveCardDisputeCommand;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.COMPENSATE_REVERSE_CHARGEBACK;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.COMPENSATE_ROLLBACK_DISPUTE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CHARGEBACK_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CREDIT_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_DEBIT_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_DISPUTE_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_ORIGINAL_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_PREVIOUS_DISPUTE_STATUS;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_RESOLUTION_OUTCOME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_RESOLUTION_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.DISPUTE_STATUS_RESOLVED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.EVENT_CARD_BALANCE_REFRESHED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.EVENT_CHARGEBACK_POSTED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.EVENT_DISPUTE_LOADED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.EVENT_DISPUTE_UPDATED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.OUTCOME_APPROVED_CARDHOLDER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.OUTCOME_APPROVED_MERCHANT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.OUTCOME_SPLIT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.RESULT_SKIPPED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.SAGA_RESOLVE_DISPUTE_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_LOAD_DISPUTE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_POST_CHARGEBACK_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_REFRESH_CARD_BALANCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_UPDATE_DISPUTE;

/**
 * Saga orchestrator for resolving a card dispute. Depending on {@code resolutionOutcome}:
 *
 * <ul>
 *   <li>{@code APPROVED_CARDHOLDER} — CREDIT cardholder account, DEBIT merchant-settlement GL.</li>
 *   <li>{@code APPROVED_MERCHANT} — no ledger movement (dispute upheld against cardholder);
 *       the step returns {@code RESULT_SKIPPED}.</li>
 *   <li>{@code SPLIT} — both movements, balanced as a four-leg chargeback.</li>
 * </ul>
 *
 * <p>Compensation reverses the chargeback post (when one was emitted) and rolls the dispute
 * status back to its previous state. All compensators are null-safe.
 */
@Slf4j
@Saga(name = SAGA_RESOLVE_DISPUTE_NAME)
@Service
public class ResolveCardDisputeSaga {

    private static final String LEG_DEBIT = "DEBIT";
    private static final String LEG_CREDIT = "CREDIT";
    private static final String TX_TYPE_CARD = "CARD";
    private static final String LINE_TYPE_CARD = "CARD";
    private static final String STATUS_POSTED = "POSTED";
    private static final String RELATION_CHARGEBACK = "CHARGEBACK";
    private static final String RELATION_REVERSAL = "REVERSAL";
    private static final String DESC_CHARGEBACK = "Card dispute chargeback";
    private static final String DESC_CHARGEBACK_REVERSAL = "Compensation — chargeback reversal";
    private static final String BALANCE_TYPE_LEDGER = "LEDGER";

    private final CommandBus commandBus;
    private final CardsApi cardsApi;
    private final CardDisputesApi cardDisputesApi;
    private final CardTransactionsApi cardTransactionsApi;
    private final CardBalancesApi cardBalancesApi;
    private final TransactionsApi transactionsApi;
    private final AccountLegsApi accountLegsApi;
    private final LedgerGlProperties ledgerGlProperties;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public ResolveCardDisputeSaga(CommandBus commandBus,
                                  CardsApi cardsApi,
                                  CardDisputesApi cardDisputesApi,
                                  CardTransactionsApi cardTransactionsApi,
                                  CardBalancesApi cardBalancesApi,
                                  TransactionsApi transactionsApi,
                                  AccountLegsApi accountLegsApi,
                                  LedgerGlProperties ledgerGlProperties) {
        this.commandBus = commandBus;
        this.cardsApi = cardsApi;
        this.cardDisputesApi = cardDisputesApi;
        this.cardTransactionsApi = cardTransactionsApi;
        this.cardBalancesApi = cardBalancesApi;
        this.transactionsApi = transactionsApi;
        this.accountLegsApi = accountLegsApi;
        this.ledgerGlProperties = ledgerGlProperties;
    }

    @SagaStep(id = STEP_LOAD_DISPUTE)
    @StepEvent(type = EVENT_DISPUTE_LOADED)
    public Mono<UUID> loadDispute(ResolveCardDisputeCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_DISPUTE_ID, cmd.getDisputeId());
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_RESOLUTION_OUTCOME, cmd.getResolutionOutcome());
        ctx.putVariable(CTX_RESOLUTION_REFERENCE, cmd.getResolutionReference());
        ctx.putVariable(CTX_CREDIT_AMOUNT, cmd.getCreditAmount());
        ctx.putVariable(CTX_DEBIT_AMOUNT, cmd.getDebitAmount());

        return cardDisputesApi.getDispute(cmd.getCardId(), cmd.getDisputeId(), UUID.randomUUID().toString())
                .flatMap(dispute -> {
                    UUID cardTransactionId = dispute.getTransactionId();
                    UUID accountId = dispute.getAccountId();
                    ctx.putVariable(CTX_CARD_TRANSACTION_ID, cardTransactionId);
                    ctx.putVariable(CTX_ACCOUNT_ID, accountId);
                    ctx.putVariable(CTX_PREVIOUS_DISPUTE_STATUS, dispute.getDisputeStatus());

                    return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                            .doOnNext(card -> ctx.putVariable(CTX_ACCOUNT_CURRENCY, card.getCurrencyCode()))
                            .then(lookupOriginalLedgerTx(cmd.getCardId(), cardTransactionId))
                            .doOnNext(id -> ctx.putVariable(CTX_ORIGINAL_LEDGER_TX_ID, id))
                            .thenReturn(cmd.getDisputeId());
                });
    }

    private Mono<UUID> lookupOriginalLedgerTx(UUID cardId, UUID cardTransactionId) {
        if (cardTransactionId == null) {
            return Mono.empty();
        }
        return cardTransactionsApi.getTransaction(cardId, cardTransactionId, UUID.randomUUID().toString())
                .flatMap(cardTx -> {
                    String externalAuthReference = cardTx.getCardTransactionReference();
                    if (externalAuthReference == null || externalAuthReference.isBlank()) {
                        return Mono.empty();
                    }
                    return transactionsApi.findByExternalReference(
                                    "card-auth:" + externalAuthReference, UUID.randomUUID().toString())
                            .map(TransactionDTO::getTransactionId);
                });
    }

    @SagaStep(id = STEP_POST_CHARGEBACK_LEDGER, compensate = COMPENSATE_REVERSE_CHARGEBACK, dependsOn = STEP_LOAD_DISPUTE)
    @StepEvent(type = EVENT_CHARGEBACK_POSTED)
    public Mono<Object> postChargebackLedger(ResolveCardDisputeCommand ignoredCmd,
                                             @CorrelationId String sagaId,
                                             ExecutionContext ctx) {
        String outcome = ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class);
        if (OUTCOME_APPROVED_MERCHANT.equals(outcome)) {
            log.info("Dispute resolution APPROVED_MERCHANT — no ledger movement required sagaId={}", sagaId);
            return Mono.just(RESULT_SKIPPED);
        }

        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        UUID merchantSettlementGl = ledgerGlProperties.getMerchantSettlementAccountId();
        UUID originalLedgerTxId = ctx.getVariableAs(CTX_ORIGINAL_LEDGER_TX_ID, UUID.class);
        String resolutionReference = ctx.getVariableAs(CTX_RESOLUTION_REFERENCE, String.class);
        BigDecimal creditAmount = ctx.getVariableAs(CTX_CREDIT_AMOUNT, BigDecimal.class);
        BigDecimal debitAmount = ctx.getVariableAs(CTX_DEBIT_AMOUNT, BigDecimal.class);

        List<LedgerLegSpec> legs = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (isPositive(creditAmount)) {
            legs.add(LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(creditAmount).currency(currency).build());
            legs.add(LedgerLegSpec.builder().accountId(merchantSettlementGl).legType(LEG_DEBIT).amount(creditAmount).currency(currency).build());
            totalAmount = totalAmount.add(creditAmount);
        }
        if (OUTCOME_SPLIT.equals(outcome) && isPositive(debitAmount)) {
            legs.add(LedgerLegSpec.builder().accountId(accountId).legType(LEG_DEBIT).amount(debitAmount).currency(currency).build());
            legs.add(LedgerLegSpec.builder().accountId(merchantSettlementGl).legType(LEG_CREDIT).amount(debitAmount).currency(currency).build());
            totalAmount = totalAmount.add(debitAmount);
        }
        if (legs.isEmpty()) {
            log.info("Dispute resolution has no non-zero amounts — skipping ledger post sagaId={}", sagaId);
            return Mono.just(RESULT_SKIPPED);
        }

        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("card-chargeback:"
                        + (resolutionReference == null ? sagaId + ":" + STEP_POST_CHARGEBACK_LEDGER : resolutionReference))
                .transactionType(TX_TYPE_CARD)
                .totalAmount(totalAmount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_CHARGEBACK)
                .legs(legs)
                .lineType(LINE_TYPE_CARD)
                .lineDto(new TransactionLineCardDTO()
                        .cardTransactionReference(resolutionReference)
                        .cardTransactionTimestamp(now))
                .relatedTransactionId(originalLedgerTxId)
                .relationType(RELATION_CHARGEBACK)
                .initialStatus(STATUS_POSTED)
                .build();

        return commandBus.<PostLedgerTransactionResult>send(cmd)
                .map(PostLedgerTransactionResult::getTransactionId)
                .doOnNext(id -> ctx.putVariable(CTX_CHARGEBACK_LEDGER_TX_ID, id))
                .doOnSuccess(id -> log.info("Posted chargeback sagaId={} chargebackTxId={} outcome={}",
                        sagaId, id, outcome))
                .map(id -> (Object) id);
    }

    /**
     * Compensation for {@link #postChargebackLedger} — posts a REVERSAL on the chargeback so
     * the merchant and cardholder positions are restored.
     */
    public Mono<Void> reverseChargeback(Object ignoredResult, @CorrelationId String sagaId, ExecutionContext ctx) {
        UUID chargebackLedgerTxId = (UUID) ctx.getVariable(CTX_CHARGEBACK_LEDGER_TX_ID);
        if (chargebackLedgerTxId == null) {
            return Mono.empty();
        }
        UUID accountId = ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class);
        String currency = ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class);
        UUID merchantSettlementGl = ledgerGlProperties.getMerchantSettlementAccountId();
        BigDecimal creditAmount = (BigDecimal) ctx.getVariable(CTX_CREDIT_AMOUNT);
        BigDecimal debitAmount = (BigDecimal) ctx.getVariable(CTX_DEBIT_AMOUNT);

        List<LedgerLegSpec> legs = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (isPositive(creditAmount)) {
            legs.add(LedgerLegSpec.builder().accountId(accountId).legType(LEG_DEBIT).amount(creditAmount).currency(currency).build());
            legs.add(LedgerLegSpec.builder().accountId(merchantSettlementGl).legType(LEG_CREDIT).amount(creditAmount).currency(currency).build());
            totalAmount = totalAmount.add(creditAmount);
        }
        if (isPositive(debitAmount)) {
            legs.add(LedgerLegSpec.builder().accountId(accountId).legType(LEG_CREDIT).amount(debitAmount).currency(currency).build());
            legs.add(LedgerLegSpec.builder().accountId(merchantSettlementGl).legType(LEG_DEBIT).amount(debitAmount).currency(currency).build());
            totalAmount = totalAmount.add(debitAmount);
        }
        if (legs.isEmpty()) {
            return Mono.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        PostLedgerTransactionCommand reversal = PostLedgerTransactionCommand.builder()
                .externalReference(sagaId + ":" + COMPENSATE_REVERSE_CHARGEBACK)
                .transactionType(TX_TYPE_CARD)
                .totalAmount(totalAmount)
                .currency(currency)
                .valueDate(now)
                .bookingDate(now)
                .description(DESC_CHARGEBACK_REVERSAL)
                .legs(legs)
                .relatedTransactionId(chargebackLedgerTxId)
                .relationType(RELATION_REVERSAL)
                .initialStatus(STATUS_POSTED)
                .build();
        return commandBus.<PostLedgerTransactionResult>send(reversal).then();
    }

    @SagaStep(id = STEP_UPDATE_DISPUTE, compensate = COMPENSATE_ROLLBACK_DISPUTE, dependsOn = STEP_POST_CHARGEBACK_LEDGER)
    @StepEvent(type = EVENT_DISPUTE_UPDATED)
    public Mono<UUID> updateDispute(ResolveCardDisputeCommand ignoredCmd, ExecutionContext ctx) {
        UUID cardId = ctx.getVariableAs(CTX_CARD_ID, UUID.class);
        UUID disputeId = ctx.getVariableAs(CTX_DISPUTE_ID, UUID.class);
        String outcome = ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class);
        BigDecimal creditAmount = ctx.getVariableAs(CTX_CREDIT_AMOUNT, BigDecimal.class);
        BigDecimal debitAmount = ctx.getVariableAs(CTX_DEBIT_AMOUNT, BigDecimal.class);
        String resolutionReference = ctx.getVariableAs(CTX_RESOLUTION_REFERENCE, String.class);

        boolean cardholderCredited = OUTCOME_APPROVED_CARDHOLDER.equals(outcome) || OUTCOME_SPLIT.equals(outcome);
        boolean merchantDebited = OUTCOME_APPROVED_CARDHOLDER.equals(outcome) || OUTCOME_SPLIT.equals(outcome);
        CardDisputeDTO patch = new CardDisputeDTO()
                .disputeStatus(DISPUTE_STATUS_RESOLVED)
                .resolutionTimestamp(LocalDateTime.now())
                .resolutionCode(outcome)
                .isCardholderCredited(cardholderCredited)
                .isMerchantDebited(merchantDebited)
                .creditAmount(creditAmount)
                .debitAmount(debitAmount)
                .providerReference(resolutionReference);

        return cardDisputesApi.updateDispute(cardId, disputeId, patch, UUID.randomUUID().toString())
                .thenReturn(disputeId);
    }

    /**
     * Compensation for {@link #updateDispute} — restores the dispute status to its previous
     * value captured during {@link #loadDispute}. Null-safe when the update never ran.
     */
    public Mono<Void> rollbackDisputeStatus(UUID ignoredResult, ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        UUID disputeId = (UUID) ctx.getVariable(CTX_DISPUTE_ID);
        String previousStatus = (String) ctx.getVariable(CTX_PREVIOUS_DISPUTE_STATUS);
        if (cardId == null || disputeId == null || previousStatus == null) {
            return Mono.empty();
        }
        CardDisputeDTO patch = new CardDisputeDTO().disputeStatus(previousStatus);
        return cardDisputesApi.updateDispute(cardId, disputeId, patch, UUID.randomUUID().toString()).then();
    }

    @SagaStep(id = STEP_REFRESH_CARD_BALANCE, dependsOn = STEP_UPDATE_DISPUTE)
    @StepEvent(type = EVENT_CARD_BALANCE_REFRESHED)
    public Mono<Object> refreshCardBalance(ResolveCardDisputeCommand ignoredCmd, ExecutionContext ctx) {
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

    private static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
