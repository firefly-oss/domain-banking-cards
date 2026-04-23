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
import com.firefly.domain.banking.cards.core.transaction.commands.ReverseCardAuthorizationCommand;
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
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_AUTH_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_CARD_TRANSACTION_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REVERSAL_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REVERSAL_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.CTX_REVERSAL_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReverseCardAuthorizationSaga — lookup, reversal post, card tx REVERSED")
class ReverseCardAuthorizationSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardsApi cardsApi;
    @Mock private CardTransactionsApi cardTransactionsApi;
    @Mock private CardBalancesApi cardBalancesApi;
    @Mock private TransactionsApi transactionsApi;
    @Mock private AccountLegsApi accountLegsApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private ReverseCardAuthorizationSaga saga;

    private static final UUID GL_CARD_AUTH_SUSPENSE = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final String SAGA_ID = "saga-reverse-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setCardAuthSuspenseAccountId(GL_CARD_AUTH_SUSPENSE);
        saga = new ReverseCardAuthorizationSaga(
                commandBus, cardsApi, cardTransactionsApi, cardBalancesApi,
                transactionsApi, accountLegsApi, glProperties);
    }

    @Test
    @DisplayName("lookupAuthorization resolves and stores auth ledger tx id + reversal amount")
    void lookupAuthorization_populatesCtx() {
        UUID cardId = UUID.randomUUID();
        UUID cardTxId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID authLedgerTxId = UUID.randomUUID();
        BigDecimal authAmount = new BigDecimal("42.50");

        when(cardTransactionsApi.getTransaction(eq(cardId), eq(cardTxId), anyString()))
                .thenReturn(Mono.just(new CardTransactionDTO().cardTransactionReference("NET-REF-123")));
        CardDTO card = new CardDTO();
        card.setAccountId(accountId);
        card.setCurrencyCode("EUR");
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(card));
        TransactionDTO authTx = new TransactionDTO();
        setField(authTx, "transactionId", authLedgerTxId);
        authTx.setTotalAmount(authAmount);
        when(transactionsApi.findByExternalReference(eq("card-auth:NET-REF-123"), anyString()))
                .thenReturn(Mono.just(authTx));

        ReverseCardAuthorizationCommand cmd = ReverseCardAuthorizationCommand.builder()
                .cardId(cardId)
                .cardTransactionId(cardTxId)
                .reason("VOID")
                .reversalReference("RVSL-001")
                .build();

        StepVerifier.create(saga.lookupAuthorization(cmd, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(authLedgerTxId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CARD_ID, cardId);
        verify(ctx).putVariable(CTX_CARD_TRANSACTION_ID, cardTxId);
        verify(ctx).putVariable(CTX_ACCOUNT_ID, accountId);
        verify(ctx).putVariable(CTX_ACCOUNT_CURRENCY, "EUR");
        verify(ctx).putVariable(CTX_AUTH_LEDGER_TX_ID, authLedgerTxId);
        verify(ctx).putVariable(CTX_REVERSAL_AMOUNT, authAmount);
    }

    @Test
    @DisplayName("postReversalLedger dispatches a POSTED REVERSAL with inverted legs")
    void postReversalLedger_dispatchesReversal() {
        UUID authLedgerTxId = UUID.randomUUID();
        UUID reversalTxId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("42.50");

        when(ctx.getVariableAs(CTX_AUTH_LEDGER_TX_ID, UUID.class)).thenReturn(authLedgerTxId);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_REVERSAL_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_REVERSAL_REFERENCE, String.class)).thenReturn("RVSL-001");
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(reversalTxId).build()));

        StepVerifier.create(saga.postReversalLedger(null, SAGA_ID, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(reversalTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand dispatched = cap.getValue();
        assertThat(dispatched.getExternalReference()).isEqualTo("card-auth-reversal:RVSL-001");
        assertThat(dispatched.getRelationType()).isEqualTo("REVERSAL");
        assertThat(dispatched.getRelatedTransactionId()).isEqualTo(authLedgerTxId);
        assertThat(dispatched.getInitialStatus()).isEqualTo("POSTED");
        assertThat(dispatched.getLegs().get(0).getAccountId()).isEqualTo(GL_CARD_AUTH_SUSPENSE);
        assertThat(dispatched.getLegs().get(0).getLegType()).isEqualTo("DEBIT");
        assertThat(dispatched.getLegs().get(1).getAccountId()).isEqualTo(accountId);
        assertThat(dispatched.getLegs().get(1).getLegType()).isEqualTo("CREDIT");

        verify(ctx).putVariable(CTX_REVERSAL_LEDGER_TX_ID, reversalTxId);
    }

    @Test
    @DisplayName("updateCardTransaction patches card_transaction.status to REVERSED")
    void updateCardTransaction_marksReversed() {
        UUID cardId = UUID.randomUUID();
        UUID cardTxId = UUID.randomUUID();
        when(ctx.getVariableAs(CTX_CARD_ID, UUID.class)).thenReturn(cardId);
        when(ctx.getVariableAs(CTX_CARD_TRANSACTION_ID, UUID.class)).thenReturn(cardTxId);
        when(cardTransactionsApi.updateTransaction(eq(cardId), eq(cardTxId), any(CardTransactionDTO.class), anyString()))
                .thenReturn(Mono.just(new CardTransactionDTO()));

        StepVerifier.create(saga.updateCardTransaction(null, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(cardTxId))
                .verifyComplete();

        ArgumentCaptor<CardTransactionDTO> cap = ArgumentCaptor.forClass(CardTransactionDTO.class);
        verify(cardTransactionsApi).updateTransaction(eq(cardId), eq(cardTxId), cap.capture(), anyString());
        assertThat(cap.getValue().getTransactionStatus())
                .isEqualTo(CardTransactionDTO.TransactionStatusEnum.REVERSED);
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
