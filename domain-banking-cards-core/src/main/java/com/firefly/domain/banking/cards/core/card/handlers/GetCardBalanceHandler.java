package com.firefly.domain.banking.cards.core.card.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.core.banking.ledger.sdk.model.PaginationResponse;
import com.firefly.core.banking.ledger.sdk.model.TransactionDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLegDTO;
import com.firefly.domain.banking.cards.core.card.queries.GetCardBalanceQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composes a card's balance reactively from the ledger rather than trusting the
 * {@code card_balance} snapshot as the source of truth:
 *
 * <ol>
 *   <li>Read the card → {@code accountId}.</li>
 *   <li>List every leg of the account via {@link AccountLegsApi#listAccountLegs}.</li>
 *   <li>Join each leg with its parent transaction to classify POSTED vs PENDING.</li>
 *   <li>Sum POSTED legs (signed debit/credit) → {@code balanceAmount}.
 *       Sum PENDING legs (absolute) → {@code pendingAmount}.</li>
 *   <li>Return a composed {@link CardBalanceDTO}. The persisted snapshot remains a
 *       read-cache maintained by sagas; this handler does not write it back.</li>
 * </ol>
 */
@Slf4j
@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardBalanceHandler extends QueryHandler<GetCardBalanceQuery, CardBalanceDTO> {

    private static final String LEG_TYPE_DEBIT = "DEBIT";
    private static final String STATUS_POSTED = "POSTED";
    private static final int LEGS_PAGE_SIZE = 1000;
    private static final int TX_LOOKUP_CONCURRENCY = 8;

    private final CardsApi cardsApi;
    private final CardBalancesApi cardBalancesApi;
    private final AccountLegsApi accountLegsApi;
    private final TransactionsApi transactionsApi;
    private final ObjectMapper objectMapper;

    @Override
    protected Mono<CardBalanceDTO> doHandle(GetCardBalanceQuery query) {
        UUID cardId = query.getCardId();
        return cardsApi.getCard(cardId, UUID.randomUUID().toString())
                .flatMap(card -> {
                    UUID accountId = card == null ? null : card.getAccountId();
                    if (accountId == null) {
                        log.warn("Card has no linked accountId — falling back to snapshot cardId={}", cardId);
                        return Mono.defer(() -> readSnapshot(cardId));
                    }
                    return composeFromLedger(cardId, card)
                            .switchIfEmpty(Mono.defer(() -> readSnapshot(cardId)));
                });
    }

    private Mono<CardBalanceDTO> composeFromLedger(UUID cardId, CardDTO card) {
        UUID accountId = card.getAccountId();
        return accountLegsApi.listAccountLegs(accountId, 0, LEGS_PAGE_SIZE, null, null, UUID.randomUUID().toString())
                .flatMapMany(this::toLegFlux)
                .collectList()
                .flatMap(legs -> classifyLegs(legs)
                        .map(amounts -> buildDto(cardId, card, amounts)));
    }

    private Mono<LedgerAmounts> classifyLegs(List<TransactionLegDTO> legs) {
        if (legs.isEmpty()) {
            return Mono.just(new LedgerAmounts());
        }
        return Flux.fromIterable(distinctTransactionIds(legs))
                .flatMap(txId -> transactionsApi.getTransaction(txId, UUID.randomUUID().toString())
                                .onErrorResume(err -> {
                                    log.warn("Failed to fetch ledger tx transactionId={} error={}",
                                            txId, err.getMessage());
                                    return Mono.empty();
                                }),
                        TX_LOOKUP_CONCURRENCY)
                .collectMap(TransactionDTO::getTransactionId, tx -> tx.getTransactionStatus() == null
                        ? null
                        : tx.getTransactionStatus().getValue())
                .map(statusByTxId -> foldLegs(legs, statusByTxId));
    }

    private List<UUID> distinctTransactionIds(List<TransactionLegDTO> legs) {
        return legs.stream()
                .map(TransactionLegDTO::getTransactionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private LedgerAmounts foldLegs(List<TransactionLegDTO> legs, Map<UUID, String> statusByTxId) {
        LedgerAmounts amounts = new LedgerAmounts();
        for (TransactionLegDTO leg : legs) {
            BigDecimal amount = leg.getAmount() == null ? BigDecimal.ZERO : leg.getAmount();
            boolean debit = LEG_TYPE_DEBIT.equalsIgnoreCase(leg.getLegType());
            BigDecimal signed = debit ? amount : amount.negate();
            String status = statusByTxId.get(leg.getTransactionId());
            if (STATUS_POSTED.equalsIgnoreCase(status)) {
                amounts.postedBalance = amounts.postedBalance.add(signed);
            } else {
                amounts.pendingHolds = amounts.pendingHolds.add(amount.abs());
            }
        }
        return amounts;
    }

    private CardBalanceDTO buildDto(UUID cardId, CardDTO card, LedgerAmounts amounts) {
        return new CardBalanceDTO()
                .cardId(cardId)
                .accountId(card.getAccountId())
                .balanceType("LEDGER")
                .balanceAmount(amounts.postedBalance)
                .pendingAmount(amounts.pendingHolds)
                .currencyCode(card.getCurrencyCode())
                .asOfDate(LocalDateTime.now());
    }

    private Mono<CardBalanceDTO> readSnapshot(UUID cardId) {
        return cardBalancesApi.getAllBalances(cardId, null, null, null, null, UUID.randomUUID().toString())
                .flatMap(response -> {
                    if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                        return Mono.empty();
                    }
                    Object first = response.getContent().get(0);
                    return Mono.just(objectMapper.convertValue(first, CardBalanceDTO.class));
                });
    }

    private Flux<TransactionLegDTO> toLegFlux(PaginationResponse page) {
        Object content = page == null ? null : page.getContent();
        if (!(content instanceof List<?> list)) {
            return Flux.empty();
        }
        return Flux.fromIterable(list).map(entry -> objectMapper.convertValue(entry, TransactionLegDTO.class));
    }

    private static final class LedgerAmounts {
        private BigDecimal postedBalance = BigDecimal.ZERO;
        private BigDecimal pendingHolds = BigDecimal.ZERO;
    }
}
