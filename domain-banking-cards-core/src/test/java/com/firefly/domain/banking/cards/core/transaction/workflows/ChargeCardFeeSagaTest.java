package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineFeeDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.ChargeCardFeeCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_FEE_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_FEE_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.CTX_FEE_WAIVED;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.FEE_TYPE_ANNUAL;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.RESULT_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChargeCardFeeSaga — happy path and waived fee")
class ChargeCardFeeSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardsApi cardsApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private ChargeCardFeeSaga saga;

    private static final UUID GL_FEE_INCOME = UUID.fromString("00000000-0000-0000-0000-000000000055");
    private static final String SAGA_ID = "saga-fee-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setFeeIncomeAccountId(GL_FEE_INCOME);
        saga = new ChargeCardFeeSaga(commandBus, cardsApi, glProperties);
    }

    @Test
    @DisplayName("postFeeLedger waived=true: returns skipped sentinel, never dispatches")
    void postFeeLedger_waivedSkips() {
        when(ctx.getVariableAs(CTX_FEE_WAIVED, Boolean.class)).thenReturn(Boolean.TRUE);

        ChargeCardFeeCommand cmd = ChargeCardFeeCommand.builder()
                .cardId(UUID.randomUUID())
                .feeType(FEE_TYPE_ANNUAL)
                .feeAmount(new BigDecimal("99.00"))
                .waived(Boolean.TRUE)
                .waiverReason("promotional")
                .build();

        StepVerifier.create(saga.postFeeLedger(cmd, SAGA_ID, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(RESULT_SKIPPED))
                .verifyComplete();

        verify(commandBus, never()).send(any(PostLedgerTransactionCommand.class));
    }

    @Test
    @DisplayName("postFeeLedger normal path: FEE ledger with DEBIT cardholder / CREDIT fee-income GL")
    void postFeeLedger_dispatchesFee() {
        UUID accountId = UUID.randomUUID();
        UUID ledgerTxId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5.00");
        when(ctx.getVariableAs(CTX_FEE_WAIVED, Boolean.class)).thenReturn(Boolean.FALSE);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_FEE_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(ledgerTxId).build()));

        ChargeCardFeeCommand cmd = ChargeCardFeeCommand.builder()
                .cardId(UUID.randomUUID())
                .feeType(FEE_TYPE_ANNUAL)
                .feeAmount(amount)
                .feeCalculationMethod("FIXED")
                .waived(Boolean.FALSE)
                .build();

        StepVerifier.create(saga.postFeeLedger(cmd, SAGA_ID, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(ledgerTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand dispatched = cap.getValue();
        assertThat(dispatched.getTransactionType()).isEqualTo("FEE");
        assertThat(dispatched.getLineType()).isEqualTo("FEE");
        assertThat(dispatched.getLineDto()).isInstanceOf(TransactionLineFeeDTO.class);
        assertThat(((TransactionLineFeeDTO) dispatched.getLineDto()).getFeeType()).isEqualTo(FEE_TYPE_ANNUAL);
        assertThat(dispatched.getLegs().get(0).getAccountId()).isEqualTo(accountId);
        assertThat(dispatched.getLegs().get(0).getLegType()).isEqualTo("DEBIT");
        assertThat(dispatched.getLegs().get(1).getAccountId()).isEqualTo(GL_FEE_INCOME);
        assertThat(dispatched.getLegs().get(1).getLegType()).isEqualTo("CREDIT");
        verify(ctx).putVariable(CTX_FEE_LEDGER_TX_ID, ledgerTxId);
    }

    @Test
    @DisplayName("reverseFeeLedger dispatches REVERSAL only when fee was actually posted")
    void reverseFeeLedger_noOpWhenSkipped() {
        when(ctx.getVariable(CTX_FEE_LEDGER_TX_ID)).thenReturn(null);

        StepVerifier.create(saga.reverseFeeLedger(null, SAGA_ID, ctx)).verifyComplete();

        verify(commandBus, never()).send(any(PostLedgerTransactionCommand.class));
    }
}
