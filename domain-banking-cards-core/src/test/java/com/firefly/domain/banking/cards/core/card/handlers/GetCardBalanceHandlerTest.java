package com.firefly.domain.banking.cards.core.card.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.core.banking.ledger.sdk.model.PaginationResponse;
import com.firefly.core.banking.ledger.sdk.model.TransactionDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLegDTO;
import com.firefly.domain.banking.cards.core.card.queries.GetCardBalanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetCardBalanceHandler — composes balance from ledger legs")
class GetCardBalanceHandlerTest {

    @Mock private CardsApi cardsApi;
    @Mock private CardBalancesApi cardBalancesApi;
    @Mock private AccountLegsApi accountLegsApi;
    @Mock private TransactionsApi transactionsApi;

    private ObjectMapper objectMapper;
    private GetCardBalanceHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        handler = new GetCardBalanceHandler(cardsApi, cardBalancesApi, accountLegsApi, transactionsApi, objectMapper);
    }

    @Test
    @DisplayName("Composes POSTED balance (signed) + PENDING holds (abs) from ledger legs")
    void composesBalanceFromLedger() {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID postedTxId = UUID.randomUUID();
        UUID pendingTxId = UUID.randomUUID();

        CardDTO card = new CardDTO();
        card.setAccountId(accountId);
        card.setCurrencyCode("EUR");
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(card));

        TransactionLegDTO postedDebit = new TransactionLegDTO()
                .transactionId(postedTxId)
                .legType("DEBIT")
                .amount(new BigDecimal("100.00"));
        TransactionLegDTO postedCredit = new TransactionLegDTO()
                .transactionId(postedTxId)
                .legType("CREDIT")
                .amount(new BigDecimal("20.00"));
        TransactionLegDTO pendingDebit = new TransactionLegDTO()
                .transactionId(pendingTxId)
                .legType("DEBIT")
                .amount(new BigDecimal("30.00"));

        PaginationResponse page = new PaginationResponse();
        setField(page, "content", List.of(postedDebit, postedCredit, pendingDebit));
        when(accountLegsApi.listAccountLegs(eq(accountId), any(), any(), any(), any(), anyString()))
                .thenReturn(Mono.just(page));

        TransactionDTO postedTx = new TransactionDTO();
        setField(postedTx, "transactionId", postedTxId);
        postedTx.setTransactionStatus(TransactionDTO.TransactionStatusEnum.POSTED);
        TransactionDTO pendingTx = new TransactionDTO();
        setField(pendingTx, "transactionId", pendingTxId);
        pendingTx.setTransactionStatus(TransactionDTO.TransactionStatusEnum.PENDING);
        when(transactionsApi.getTransaction(eq(postedTxId), anyString())).thenReturn(Mono.just(postedTx));
        when(transactionsApi.getTransaction(eq(pendingTxId), anyString())).thenReturn(Mono.just(pendingTx));

        StepVerifier.create(handler.doHandle(GetCardBalanceQuery.builder().cardId(cardId).build()))
                .assertNext(balance -> {
                    assertThat(balance.getBalanceAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
                    assertThat(balance.getPendingAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
                    assertThat(balance.getBalanceType()).isEqualTo("LEDGER");
                    assertThat(balance.getCurrencyCode()).isEqualTo("EUR");
                    assertThat(balance.getCardId()).isEqualTo(cardId);
                    assertThat(balance.getAccountId()).isEqualTo(accountId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Falls back to card_balance snapshot when the card has no linked accountId")
    void fallsBackToSnapshotWhenNoAccount() {
        UUID cardId = UUID.randomUUID();
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(new CardDTO()));

        com.firefly.core.banking.cards.sdk.model.PaginationResponse cardsPage = new com.firefly.core.banking.cards.sdk.model.PaginationResponse();
        com.firefly.core.banking.cards.sdk.model.CardBalanceDTO snapshot =
                new com.firefly.core.banking.cards.sdk.model.CardBalanceDTO()
                        .balanceAmount(new BigDecimal("12.00"))
                        .balanceType("SNAPSHOT");
        setField(cardsPage, "content", List.of(snapshot));
        when(cardBalancesApi.getAllBalances(eq(cardId), any(), any(), any(), any(), anyString()))
                .thenReturn(Mono.just(cardsPage));

        StepVerifier.create(handler.doHandle(GetCardBalanceQuery.builder().cardId(cardId).build()))
                .assertNext(balance -> assertThat(balance.getBalanceType()).isEqualTo("SNAPSHOT"))
                .verifyComplete();
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not reflect-set " + name, e);
        }
    }
}
