package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.cards.sdk.model.CardTransactionDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.ClearCardTransactionCommand;
import com.firefly.domain.banking.cards.infra.properties.LedgerGlProperties;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CLEARING_PATH_REVERSAL_PLUS_NEW;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CLEARING_PATH_TRANSITION;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_AUTHORIZED_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_AUTH_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CLEARED_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CLEARING_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_CLEARING_PATH;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_NETWORK_CLEARING_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.CTX_SETTLEMENT_TIMESTAMP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClearCardTransactionSaga — transition vs REVERSAL+NEW paths, compensation")
class ClearCardTransactionSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardsApi cardsApi;
    @Mock private CardTransactionsApi cardTransactionsApi;
    @Mock private CardBalancesApi cardBalancesApi;
    @Mock private TransactionsApi transactionsApi;
    @Mock private AccountLegsApi accountLegsApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private ClearCardTransactionSaga saga;

    private static final UUID GL_CARD_AUTH_SUSPENSE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String SAGA_ID = "saga-clear-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setCardAuthSuspenseAccountId(GL_CARD_AUTH_SUSPENSE);
        saga = new ClearCardTransactionSaga(
                commandBus, cardsApi, cardTransactionsApi, cardBalancesApi,
                transactionsApi, accountLegsApi, glProperties);
    }

    @Test
    @DisplayName("lookupAuthorization caches cardTransactionId, accountId, currency, and auth ledger tx id in ctx")
    void lookupAuthorization_populatesCtx() {
        UUID cardId = UUID.randomUUID();
        UUID cardTxId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID authLedgerTxId = UUID.randomUUID();
        BigDecimal authorizedAmount = new BigDecimal("42.50");

        CardTransactionDTO cardTx = new CardTransactionDTO().cardTransactionReference("NET-REF-123");
        when(cardTransactionsApi.getTransaction(eq(cardId), eq(cardTxId), anyString()))
                .thenReturn(Mono.just(cardTx));
        CardDTO card = new CardDTO();
        card.setAccountId(accountId);
        card.setCurrencyCode("EUR");
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(card));

        TransactionDTO authTx = new TransactionDTO();
        setField(authTx, "transactionId", authLedgerTxId);
        authTx.setTotalAmount(authorizedAmount);
        when(transactionsApi.findByExternalReference(eq("card-auth:NET-REF-123"), anyString()))
                .thenReturn(Mono.just(authTx));

        ClearCardTransactionCommand cmd = ClearCardTransactionCommand.builder()
                .cardId(cardId)
                .cardTransactionId(cardTxId)
                .clearedAmount(new BigDecimal("42.50"))
                .settlementTimestamp(LocalDateTime.now())
                .networkClearingReference("CLR-REF-001")
                .build();

        StepVerifier.create(saga.lookupAuthorization(cmd, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(authLedgerTxId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CARD_ID, cardId);
        verify(ctx).putVariable(CTX_CARD_TRANSACTION_ID, cardTxId);
        verify(ctx).putVariable(CTX_ACCOUNT_ID, accountId);
        verify(ctx).putVariable(CTX_ACCOUNT_CURRENCY, "EUR");
        verify(ctx).putVariable(CTX_AUTH_LEDGER_TX_ID, authLedgerTxId);
        verify(ctx).putVariable(CTX_AUTHORIZED_AMOUNT, authorizedAmount);
    }

    @Test
    @DisplayName("postClearingLedger TRANSITION path: cleared == authorized → updateTransactionStatus to POSTED")
    void postClearingLedger_transitionPath() {
        UUID authLedgerTxId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.50");
        LocalDateTime settlement = LocalDateTime.now();

        when(ctx.getVariableAs(CTX_AUTH_LEDGER_TX_ID, UUID.class)).thenReturn(authLedgerTxId);
        when(ctx.getVariableAs(CTX_AUTHORIZED_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_CLEARED_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_SETTLEMENT_TIMESTAMP, LocalDateTime.class)).thenReturn(settlement);

        TransactionDTO transitioned = new TransactionDTO();
        setField(transitioned, "transactionId", authLedgerTxId);
        when(transactionsApi.updateTransactionStatus(eq(authLedgerTxId), eq("POSTED"), anyString(), anyString()))
                .thenReturn(Mono.just(transitioned));

        StepVerifier.create(saga.postClearingLedger(null, SAGA_ID, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(authLedgerTxId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CLEARING_PATH, CLEARING_PATH_TRANSITION);
        verify(ctx).putVariable(CTX_CLEARING_LEDGER_TX_ID, authLedgerTxId);
    }

    @Test
    @DisplayName("postClearingLedger REVERSAL+NEW path: cleared != authorized → dispatches two commands")
    void postClearingLedger_reversalPlusNewPath() {
        UUID authLedgerTxId = UUID.randomUUID();
        UUID newLedgerTxId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BigDecimal authorized = new BigDecimal("42.50");
        BigDecimal cleared = new BigDecimal("38.75");

        when(ctx.getVariableAs(CTX_AUTH_LEDGER_TX_ID, UUID.class)).thenReturn(authLedgerTxId);
        when(ctx.getVariableAs(CTX_AUTHORIZED_AMOUNT, BigDecimal.class)).thenReturn(authorized);
        when(ctx.getVariableAs(CTX_CLEARED_AMOUNT, BigDecimal.class)).thenReturn(cleared);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_SETTLEMENT_TIMESTAMP, LocalDateTime.class)).thenReturn(null);
        when(ctx.getVariableAs(CTX_NETWORK_CLEARING_REFERENCE, String.class)).thenReturn("CLR-REF-001");

        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(UUID.randomUUID()).build()))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(newLedgerTxId).build()));

        StepVerifier.create(saga.postClearingLedger(null, SAGA_ID, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(newLedgerTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus, org.mockito.Mockito.times(2)).send(cap.capture());
        assertThat(cap.getAllValues().get(0).getRelationType()).isEqualTo("REVERSAL");
        assertThat(cap.getAllValues().get(1).getRelationType()).isEqualTo("ADJUSTMENT");
        verify(ctx).putVariable(CTX_CLEARING_PATH, CLEARING_PATH_REVERSAL_PLUS_NEW);
    }

    @Test
    @DisplayName("updateCardTransaction patches status to COMPLETED with settlement timestamp")
    void updateCardTransaction_patchesCompleted() {
        UUID cardId = UUID.randomUUID();
        UUID cardTxId = UUID.randomUUID();
        LocalDateTime settlement = LocalDateTime.now();
        when(ctx.getVariableAs(CTX_CARD_ID, UUID.class)).thenReturn(cardId);
        when(ctx.getVariableAs(CTX_CARD_TRANSACTION_ID, UUID.class)).thenReturn(cardTxId);
        when(ctx.getVariableAs(CTX_SETTLEMENT_TIMESTAMP, LocalDateTime.class)).thenReturn(settlement);
        when(cardTransactionsApi.updateTransaction(eq(cardId), eq(cardTxId), any(CardTransactionDTO.class), anyString()))
                .thenReturn(Mono.just(new CardTransactionDTO()));

        StepVerifier.create(saga.updateCardTransaction(null, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(cardTxId))
                .verifyComplete();

        ArgumentCaptor<CardTransactionDTO> cap = ArgumentCaptor.forClass(CardTransactionDTO.class);
        verify(cardTransactionsApi).updateTransaction(eq(cardId), eq(cardTxId), cap.capture(), anyString());
        assertThat(cap.getValue().getTransactionStatus()).isEqualTo(CardTransactionDTO.TransactionStatusEnum.COMPLETED);
        assertThat(cap.getValue().getCardTransactionTimestamp()).isEqualTo(settlement);
    }

    @Test
    @DisplayName("reverseClearingLedger with TRANSITION path calls updateTransactionStatus back to PENDING")
    void reverseClearingLedger_transitionRollback() {
        UUID clearingLedgerTxId = UUID.randomUUID();
        when(ctx.getVariable(CTX_CLEARING_LEDGER_TX_ID)).thenReturn(clearingLedgerTxId);
        when(ctx.getVariable(CTX_CLEARING_PATH)).thenReturn(CLEARING_PATH_TRANSITION);
        when(transactionsApi.updateTransactionStatus(eq(clearingLedgerTxId), eq("PENDING"), anyString(), anyString()))
                .thenReturn(Mono.just(new TransactionDTO()));

        StepVerifier.create(saga.reverseClearingLedger(null, SAGA_ID, ctx)).verifyComplete();
    }

    @Test
    @DisplayName("reverseClearingLedger is a no-op when the clearing step never ran")
    void reverseClearingLedger_noOp() {
        when(ctx.getVariable(CTX_CLEARING_LEDGER_TX_ID)).thenReturn(null);

        StepVerifier.create(saga.reverseClearingLedger(null, SAGA_ID, ctx)).verifyComplete();
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
