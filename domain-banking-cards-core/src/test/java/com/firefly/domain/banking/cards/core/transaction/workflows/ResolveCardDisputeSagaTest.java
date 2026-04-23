package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.api.CardDisputesApi;
import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDisputeDTO;
import com.firefly.core.banking.ledger.sdk.api.AccountLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.ResolveCardDisputeCommand;
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

import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CARD_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CHARGEBACK_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_CREDIT_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_DEBIT_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_DISPUTE_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_ORIGINAL_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_RESOLUTION_OUTCOME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.CTX_RESOLUTION_REFERENCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.OUTCOME_APPROVED_CARDHOLDER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.OUTCOME_APPROVED_MERCHANT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.OUTCOME_SPLIT;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.RESULT_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResolveCardDisputeSaga — APPROVED_CARDHOLDER / APPROVED_MERCHANT / SPLIT outcomes")
class ResolveCardDisputeSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardsApi cardsApi;
    @Mock private CardDisputesApi cardDisputesApi;
    @Mock private CardTransactionsApi cardTransactionsApi;
    @Mock private CardBalancesApi cardBalancesApi;
    @Mock private TransactionsApi transactionsApi;
    @Mock private AccountLegsApi accountLegsApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private ResolveCardDisputeSaga saga;

    private static final UUID GL_MERCHANT_SETTLEMENT = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final String SAGA_ID = "saga-dispute-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setMerchantSettlementAccountId(GL_MERCHANT_SETTLEMENT);
        saga = new ResolveCardDisputeSaga(
                commandBus, cardsApi, cardDisputesApi, cardTransactionsApi, cardBalancesApi,
                transactionsApi, accountLegsApi, glProperties);
    }

    @Test
    @DisplayName("postChargebackLedger APPROVED_CARDHOLDER: 2 legs — CREDIT account + DEBIT merchant GL")
    void postChargebackLedger_approvedCardholder() {
        UUID accountId = UUID.randomUUID();
        UUID chargebackLedgerTxId = UUID.randomUUID();
        UUID originalLedgerTxId = UUID.randomUUID();
        BigDecimal creditAmount = new BigDecimal("20.00");

        when(ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class)).thenReturn(OUTCOME_APPROVED_CARDHOLDER);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_ORIGINAL_LEDGER_TX_ID, UUID.class)).thenReturn(originalLedgerTxId);
        when(ctx.getVariableAs(CTX_RESOLUTION_REFERENCE, String.class)).thenReturn("RES-001");
        when(ctx.getVariableAs(CTX_CREDIT_AMOUNT, BigDecimal.class)).thenReturn(creditAmount);
        when(ctx.getVariableAs(CTX_DEBIT_AMOUNT, BigDecimal.class)).thenReturn(BigDecimal.ZERO);
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(chargebackLedgerTxId).build()));

        StepVerifier.create(saga.postChargebackLedger(null, SAGA_ID, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(chargebackLedgerTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand dispatched = cap.getValue();
        assertThat(dispatched.getLegs()).hasSize(2);
        assertThat(dispatched.getLegs().get(0).getLegType()).isEqualTo("CREDIT");
        assertThat(dispatched.getLegs().get(0).getAccountId()).isEqualTo(accountId);
        assertThat(dispatched.getLegs().get(1).getLegType()).isEqualTo("DEBIT");
        assertThat(dispatched.getLegs().get(1).getAccountId()).isEqualTo(GL_MERCHANT_SETTLEMENT);
        assertThat(dispatched.getRelationType()).isEqualTo("CHARGEBACK");
        assertThat(dispatched.getRelatedTransactionId()).isEqualTo(originalLedgerTxId);
        verify(ctx).putVariable(CTX_CHARGEBACK_LEDGER_TX_ID, chargebackLedgerTxId);
    }

    @Test
    @DisplayName("postChargebackLedger APPROVED_MERCHANT: returns skipped sentinel, never dispatches")
    void postChargebackLedger_approvedMerchantSkips() {
        when(ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class)).thenReturn(OUTCOME_APPROVED_MERCHANT);

        StepVerifier.create(saga.postChargebackLedger(null, SAGA_ID, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(RESULT_SKIPPED))
                .verifyComplete();

        verify(commandBus, never()).send(any(PostLedgerTransactionCommand.class));
    }

    @Test
    @DisplayName("postChargebackLedger SPLIT: 4 legs covering credit + debit portions")
    void postChargebackLedger_split() {
        UUID accountId = UUID.randomUUID();
        BigDecimal creditAmount = new BigDecimal("15.00");
        BigDecimal debitAmount = new BigDecimal("7.50");

        when(ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class)).thenReturn(OUTCOME_SPLIT);
        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_ORIGINAL_LEDGER_TX_ID, UUID.class)).thenReturn(null);
        when(ctx.getVariableAs(CTX_RESOLUTION_REFERENCE, String.class)).thenReturn("RES-SPLIT");
        when(ctx.getVariableAs(CTX_CREDIT_AMOUNT, BigDecimal.class)).thenReturn(creditAmount);
        when(ctx.getVariableAs(CTX_DEBIT_AMOUNT, BigDecimal.class)).thenReturn(debitAmount);
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(UUID.randomUUID()).build()));

        StepVerifier.create(saga.postChargebackLedger(null, SAGA_ID, ctx))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        assertThat(cap.getValue().getLegs()).hasSize(4);
        assertThat(cap.getValue().getTotalAmount()).isEqualByComparingTo(creditAmount.add(debitAmount));
    }

    @Test
    @DisplayName("updateDispute patches status RESOLVED with cardholderCredited / merchantDebited flags for APPROVED_CARDHOLDER")
    void updateDispute_approvedCardholderFlags() {
        UUID cardId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        when(ctx.getVariableAs(CTX_CARD_ID, UUID.class)).thenReturn(cardId);
        when(ctx.getVariableAs(CTX_DISPUTE_ID, UUID.class)).thenReturn(disputeId);
        when(ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class)).thenReturn(OUTCOME_APPROVED_CARDHOLDER);
        when(ctx.getVariableAs(CTX_CREDIT_AMOUNT, BigDecimal.class)).thenReturn(new BigDecimal("20.00"));
        when(ctx.getVariableAs(CTX_DEBIT_AMOUNT, BigDecimal.class)).thenReturn(BigDecimal.ZERO);
        when(ctx.getVariableAs(CTX_RESOLUTION_REFERENCE, String.class)).thenReturn("RES-001");
        when(cardDisputesApi.updateDispute(eq(cardId), eq(disputeId), any(CardDisputeDTO.class), anyString()))
                .thenReturn(Mono.just(new CardDisputeDTO()));

        StepVerifier.create(saga.updateDispute(null, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(disputeId))
                .verifyComplete();

        ArgumentCaptor<CardDisputeDTO> cap = ArgumentCaptor.forClass(CardDisputeDTO.class);
        verify(cardDisputesApi).updateDispute(eq(cardId), eq(disputeId), cap.capture(), anyString());
        CardDisputeDTO patch = cap.getValue();
        assertThat(patch.getDisputeStatus()).isEqualTo("RESOLVED");
        assertThat(patch.getResolutionCode()).isEqualTo(OUTCOME_APPROVED_CARDHOLDER);
        assertThat(patch.getIsCardholderCredited()).isTrue();
        assertThat(patch.getIsMerchantDebited()).isTrue();
    }

    @Test
    @DisplayName("updateDispute does NOT flag cardholderCredited when outcome is APPROVED_MERCHANT")
    void updateDispute_approvedMerchantFlags() {
        UUID cardId = UUID.randomUUID();
        UUID disputeId = UUID.randomUUID();
        when(ctx.getVariableAs(CTX_CARD_ID, UUID.class)).thenReturn(cardId);
        when(ctx.getVariableAs(CTX_DISPUTE_ID, UUID.class)).thenReturn(disputeId);
        when(ctx.getVariableAs(CTX_RESOLUTION_OUTCOME, String.class)).thenReturn(OUTCOME_APPROVED_MERCHANT);
        when(ctx.getVariableAs(CTX_CREDIT_AMOUNT, BigDecimal.class)).thenReturn(BigDecimal.ZERO);
        when(ctx.getVariableAs(CTX_DEBIT_AMOUNT, BigDecimal.class)).thenReturn(BigDecimal.ZERO);
        when(ctx.getVariableAs(CTX_RESOLUTION_REFERENCE, String.class)).thenReturn("RES-MERCH");
        when(cardDisputesApi.updateDispute(eq(cardId), eq(disputeId), any(CardDisputeDTO.class), anyString()))
                .thenReturn(Mono.just(new CardDisputeDTO()));

        StepVerifier.create(saga.updateDispute(null, ctx)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<CardDisputeDTO> cap = ArgumentCaptor.forClass(CardDisputeDTO.class);
        verify(cardDisputesApi).updateDispute(eq(cardId), eq(disputeId), cap.capture(), anyString());
        assertThat(cap.getValue().getIsCardholderCredited()).isFalse();
        assertThat(cap.getValue().getIsMerchantDebited()).isFalse();
    }
}
