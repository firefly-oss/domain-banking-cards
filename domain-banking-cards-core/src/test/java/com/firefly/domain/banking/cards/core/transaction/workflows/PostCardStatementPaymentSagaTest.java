package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardPaymentsApi;
import com.firefly.core.banking.cards.sdk.model.CardPaymentDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineTransferDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.PostCardStatementPaymentCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_CARD_PAYMENT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_FUNDING_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_PAYMENT_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_PAYMENT_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.CTX_PAYMENT_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.RESULT_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostCardStatementPaymentSaga — create payment, post ledger TRANSFER, skip statement update")
class PostCardStatementPaymentSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardPaymentsApi cardPaymentsApi;
    @Mock private CardBalancesApi cardBalancesApi;
    @Mock private AccountLegsApi accountLegsApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private PostCardStatementPaymentSaga saga;

    private static final UUID GL_CREDIT_CARD_RECEIVABLE = UUID.fromString("00000000-0000-0000-0000-000000000033");
    private static final String SAGA_ID = "saga-stmt-pay-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setCreditCardReceivableAccountId(GL_CREDIT_CARD_RECEIVABLE);
        saga = new PostCardStatementPaymentSaga(commandBus, cardPaymentsApi, cardBalancesApi, accountLegsApi, glProperties);
    }

    @Test
    @DisplayName("createCardPayment creates a PENDING payment with the paymentReference as idempotency key")
    void createCardPayment_passesRefAsIdempotencyKey() {
        UUID cardId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");
        PostCardStatementPaymentCommand cmd = PostCardStatementPaymentCommand.builder()
                .cardId(cardId)
                .fundingAccountId(UUID.randomUUID())
                .statementId(UUID.randomUUID())
                .paymentAmount(amount)
                .currency("EUR")
                .isAutoPayment(false)
                .isFullPayment(true)
                .paymentReference("PAY-REF-001")
                .build();

        CardPaymentDTO created = new CardPaymentDTO().cardId(cardId);
        setField(created, "paymentId", paymentId);
        when(cardPaymentsApi.createPayment(eq(cardId), any(CardPaymentDTO.class), eq("PAY-REF-001")))
                .thenReturn(Mono.just(created));

        StepVerifier.create(saga.createCardPayment(cmd, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(paymentId))
                .verifyComplete();

        verify(ctx).putVariable(CTX_CARD_PAYMENT_ID, paymentId);

        ArgumentCaptor<CardPaymentDTO> cap = ArgumentCaptor.forClass(CardPaymentDTO.class);
        verify(cardPaymentsApi).createPayment(eq(cardId), cap.capture(), eq("PAY-REF-001"));
        assertThat(cap.getValue().getPaymentStatus()).isEqualTo("PENDING");
        assertThat(cap.getValue().getPaymentAmount()).isEqualByComparingTo(amount);
    }

    @Test
    @DisplayName("postPaymentLedger dispatches a TRANSFER with DEBIT funding / CREDIT receivable GL")
    void postPaymentLedger_dispatchesTransfer() {
        UUID fundingAccountId = UUID.randomUUID();
        UUID ledgerTxId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");

        when(ctx.getVariableAs(CTX_FUNDING_ACCOUNT_ID, UUID.class)).thenReturn(fundingAccountId);
        when(ctx.getVariableAs(CTX_PAYMENT_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_PAYMENT_REFERENCE, String.class)).thenReturn("PAY-REF-001");
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(ledgerTxId).build()));

        StepVerifier.create(saga.postPaymentLedger(null, SAGA_ID, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(ledgerTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand dispatched = cap.getValue();
        assertThat(dispatched.getExternalReference()).isEqualTo("card-statement-payment:PAY-REF-001");
        assertThat(dispatched.getTransactionType()).isEqualTo("TRANSFER");
        assertThat(dispatched.getInitialStatus()).isEqualTo("POSTED");
        assertThat(dispatched.getLegs().get(0).getAccountId()).isEqualTo(fundingAccountId);
        assertThat(dispatched.getLegs().get(0).getLegType()).isEqualTo("DEBIT");
        assertThat(dispatched.getLegs().get(1).getAccountId()).isEqualTo(GL_CREDIT_CARD_RECEIVABLE);
        assertThat(dispatched.getLegs().get(1).getLegType()).isEqualTo("CREDIT");
        assertThat(dispatched.getLineType()).isEqualTo("TRANSFER");
        assertThat(dispatched.getLineDto()).isInstanceOf(TransactionLineTransferDTO.class);
        assertThat(((TransactionLineTransferDTO) dispatched.getLineDto()).getTransferPurpose())
                .isEqualTo("CARD_STATEMENT_PAYMENT");
        verify(ctx).putVariable(CTX_PAYMENT_LEDGER_TX_ID, ledgerTxId);
    }

    @Test
    @DisplayName("updateStatement returns skipped sentinel (SDK has no statement mutation API)")
    void updateStatement_isSkipped() {
        StepVerifier.create(saga.updateStatement(null, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(RESULT_SKIPPED))
                .verifyComplete();
    }

    @Test
    @DisplayName("reversePaymentLedger posts a REVERSAL for the TRANSFER")
    void reversePaymentLedger_dispatchesReversal() {
        UUID paymentLedgerTxId = UUID.randomUUID();
        UUID fundingAccountId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("500.00");

        when(ctx.getVariable(CTX_PAYMENT_LEDGER_TX_ID)).thenReturn(paymentLedgerTxId);
        when(ctx.getVariableAs(CTX_FUNDING_ACCOUNT_ID, UUID.class)).thenReturn(fundingAccountId);
        when(ctx.getVariableAs(CTX_PAYMENT_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(ctx.getVariableAs(CTX_CURRENCY, String.class)).thenReturn("EUR");
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(UUID.randomUUID()).build()));

        StepVerifier.create(saga.reversePaymentLedger(null, SAGA_ID, ctx)).verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        assertThat(cap.getValue().getRelationType()).isEqualTo("REVERSAL");
        assertThat(cap.getValue().getRelatedTransactionId()).isEqualTo(paymentLedgerTxId);
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
