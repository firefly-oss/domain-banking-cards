package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.cards.sdk.model.CardTransactionDTO;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.AuthorizeCardTransactionCommand;
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

import java.math.BigDecimal;
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CARD_BALANCE_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_EXTERNAL_AUTH_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.CTX_LEDGER_TX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizeCardTransactionSaga — happy path, decline compensation, pending-balance projection")
class AuthorizeCardTransactionSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardsApi cardsApi;
    @Mock private CardTransactionsApi cardTransactionsApi;
    @Mock private CardBalancesApi cardBalancesApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private AuthorizeCardTransactionSaga saga;

    private static final UUID GL_CARD_AUTH_SUSPENSE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String SAGA_ID = "saga-auth-001";
    private static final String EXTERNAL_AUTH_REF = "net-auth-abc-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setCardAuthSuspenseAccountId(GL_CARD_AUTH_SUSPENSE);
        saga = new AuthorizeCardTransactionSaga(commandBus, cardsApi, cardTransactionsApi, cardBalancesApi, glProperties);
    }

    @Test
    @DisplayName("resolveAccount stores cardId/accountId/currency in ctx and returns the underlying accountId")
    void resolveAccount_storesCtxVarsAndReturnsAccountId() {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        CardDTO card = new CardDTO();
        card.setAccountId(accountId);
        card.setCurrencyCode("EUR");
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(card));

        AuthorizeCardTransactionCommand cmd = AuthorizeCardTransactionCommand.builder()
                .cardId(cardId)
                .amount(new BigDecimal("42.50"))
                .currency("EUR")
                .externalAuthReference(EXTERNAL_AUTH_REF)
                .build();

        StepVerifier.create(saga.resolveAccount(cmd, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(accountId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CARD_ID, cardId);
        verify(ctx).putVariable(CTX_AMOUNT, cmd.getAmount());
        verify(ctx).putVariable(CTX_CURRENCY, "EUR");
        verify(ctx).putVariable(CTX_EXTERNAL_AUTH_REFERENCE, EXTERNAL_AUTH_REF);
        verify(ctx).putVariable(CTX_ACCOUNT_ID, accountId);
        verify(ctx).putVariable(CTX_ACCOUNT_CURRENCY, "EUR");
    }

    @Test
    @DisplayName("resolveAccount fails when the card has no linked accountId")
    void resolveAccount_failsWhenNoAccountLinked() {
        UUID cardId = UUID.randomUUID();
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(new CardDTO()));

        AuthorizeCardTransactionCommand cmd = AuthorizeCardTransactionCommand.builder()
                .cardId(cardId)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .externalAuthReference(EXTERNAL_AUTH_REF)
                .build();

        StepVerifier.create(saga.resolveAccount(cmd, ctx))
                .expectErrorMessage("card " + cardId + " has no linked accountId")
                .verify();
    }

    @Test
    @DisplayName("createCardTransaction dispatches createTransaction with externalAuthReference as the idempotency key")
    void createCardTransaction_passesAuthRefAsIdempotencyKey() {
        UUID cardId = UUID.randomUUID();
        UUID newCardTxId = UUID.randomUUID();
        when(ctx.getVariableAs(CTX_CARD_ID, UUID.class)).thenReturn(cardId);
        when(ctx.getVariableAs(CTX_EXTERNAL_AUTH_REFERENCE, String.class)).thenReturn(EXTERNAL_AUTH_REF);
        when(ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class)).thenReturn(new BigDecimal("10.00"));
        when(ctx.getVariableAs(CTX_CURRENCY, String.class)).thenReturn("EUR");

        CardTransactionDTO created = new CardTransactionDTO().cardId(cardId);
        setField(created, tryField(CardTransactionDTO.class, "cardTransactionId"), newCardTxId);
        when(cardTransactionsApi.createTransaction(eq(cardId), any(CardTransactionDTO.class), eq(EXTERNAL_AUTH_REF)))
                .thenReturn(Mono.just(created));

        StepVerifier.create(saga.createCardTransaction(null, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(newCardTxId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CARD_TRANSACTION_ID, newCardTxId);
    }

    @Test
    @DisplayName("placeLedgerHold dispatches PostLedgerTransactionCommand with PENDING status and balanced legs")
    void placeLedgerHold_dispatchesPendingLedgerTx() {
        UUID accountId = UUID.randomUUID();
        UUID ledgerTxId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.50");
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_EXTERNAL_AUTH_REFERENCE, String.class)).thenReturn(EXTERNAL_AUTH_REF);
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(ledgerTxId).build()));

        StepVerifier.create(saga.placeLedgerHold(null, SAGA_ID, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(ledgerTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand dispatched = cap.getValue();
        assertThat(dispatched.getExternalReference()).isEqualTo("card-auth:" + EXTERNAL_AUTH_REF);
        assertThat(dispatched.getTransactionType()).isEqualTo("CARD");
        assertThat(dispatched.getInitialStatus()).isEqualTo("PENDING");
        assertThat(dispatched.getLegs()).hasSize(2);
        assertThat(dispatched.getLegs().get(0).getAccountId()).isEqualTo(accountId);
        assertThat(dispatched.getLegs().get(0).getLegType()).isEqualTo("DEBIT");
        assertThat(dispatched.getLegs().get(1).getAccountId()).isEqualTo(GL_CARD_AUTH_SUSPENSE);
        assertThat(dispatched.getLegs().get(1).getLegType()).isEqualTo("CREDIT");
        assertThat(dispatched.getLineType()).isEqualTo("CARD");
        verify(ctx).putVariable(CTX_LEDGER_TX_ID, ledgerTxId);
    }

    @Test
    @DisplayName("reverseLedgerHold with stored tx id dispatches a POSTED REVERSAL with inverted legs")
    void reverseLedgerHold_dispatchesReversal() {
        UUID ledgerTxId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.50");
        when(ctx.getVariable(CTX_LEDGER_TX_ID)).thenReturn(ledgerTxId);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(UUID.randomUUID()).build()));

        StepVerifier.create(saga.reverseLedgerHold(null, SAGA_ID, ctx)).verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand reversal = cap.getValue();
        assertThat(reversal.getRelatedTransactionId()).isEqualTo(ledgerTxId);
        assertThat(reversal.getRelationType()).isEqualTo("REVERSAL");
        assertThat(reversal.getInitialStatus()).isEqualTo("POSTED");
        assertThat(reversal.getLegs().get(0).getAccountId()).isEqualTo(GL_CARD_AUTH_SUSPENSE);
        assertThat(reversal.getLegs().get(0).getLegType()).isEqualTo("DEBIT");
        assertThat(reversal.getLegs().get(1).getAccountId()).isEqualTo(accountId);
        assertThat(reversal.getLegs().get(1).getLegType()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("reverseLedgerHold is a no-op when the hold step never ran (null CTX_LEDGER_TX_ID)")
    void reverseLedgerHold_noOpWhenNoLedgerTxId() {
        when(ctx.getVariable(CTX_LEDGER_TX_ID)).thenReturn(null);

        StepVerifier.create(saga.reverseLedgerHold(null, SAGA_ID, ctx)).verifyComplete();

        verify(commandBus, never()).send(any(PostLedgerTransactionCommand.class));
    }

    @Test
    @DisplayName("failCardTransaction marks card_transaction FAILED when compensation has identifiers in ctx")
    void failCardTransaction_marksFailed() {
        UUID cardId = UUID.randomUUID();
        UUID cardTxId = UUID.randomUUID();
        when(ctx.getVariable(CTX_CARD_TRANSACTION_ID)).thenReturn(cardTxId);
        when(ctx.getVariable(CTX_CARD_ID)).thenReturn(cardId);
        when(cardTransactionsApi.updateTransaction(eq(cardId), eq(cardTxId), any(CardTransactionDTO.class), anyString()))
                .thenReturn(Mono.just(new CardTransactionDTO()));

        StepVerifier.create(saga.failCardTransaction(null, ctx)).verifyComplete();

        ArgumentCaptor<CardTransactionDTO> cap = ArgumentCaptor.forClass(CardTransactionDTO.class);
        verify(cardTransactionsApi).updateTransaction(eq(cardId), eq(cardTxId), cap.capture(), anyString());
        assertThat(cap.getValue().getTransactionStatus())
                .isEqualTo(CardTransactionDTO.TransactionStatusEnum.FAILED);
    }

    @Test
    @DisplayName("failCardTransaction is a no-op when the create step never ran")
    void failCardTransaction_noOpWithoutTxId() {
        when(ctx.getVariable(CTX_CARD_TRANSACTION_ID)).thenReturn(null);

        StepVerifier.create(saga.failCardTransaction(null, ctx)).verifyComplete();
    }

    @Test
    @DisplayName("updateCardBalanceProjection creates a new pending-authorizations balance when none exists")
    void updateCardBalanceProjection_createsBalanceWhenMissing() {
        UUID cardId = UUID.randomUUID();
        UUID newBalanceId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.50");
        when(ctx.getVariableAs(CTX_CARD_ID, UUID.class)).thenReturn(cardId);
        when(ctx.getVariableAs(CTX_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_CURRENCY, String.class)).thenReturn("EUR");
        PaginationResponse emptyPage = new PaginationResponse();
        when(cardBalancesApi.getAllBalances(eq(cardId), any(), any(), any(), any(), anyString()))
                .thenReturn(Mono.just(emptyPage));

        CardBalanceDTO created = new CardBalanceDTO().cardId(cardId).pendingAmount(amount);
        java.lang.reflect.Field balanceIdField = tryField(CardBalanceDTO.class, "balanceId");
        setField(created, balanceIdField, newBalanceId);
        when(cardBalancesApi.createBalance(eq(cardId), any(CardBalanceDTO.class), anyString()))
                .thenReturn(Mono.just(created));

        StepVerifier.create(saga.updateCardBalanceProjection(null, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(newBalanceId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CARD_BALANCE_ID, newBalanceId);
    }

    private static java.lang.reflect.Field tryField(Class<?> clazz, String name) {
        try {
            java.lang.reflect.Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Missing field " + name + " on " + clazz.getSimpleName(), e);
        }
    }

    private static void setField(Object target, java.lang.reflect.Field f, Object value) {
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
