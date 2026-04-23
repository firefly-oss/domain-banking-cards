package com.firefly.domain.banking.cards.core.transaction.workflows;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineInterestDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import com.firefly.domain.banking.cards.core.transaction.commands.AccrueCardInterestCommand;
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
import java.time.LocalDate;
import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_ACCOUNT_CURRENCY;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_ACCOUNT_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_INTEREST_AMOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.CTX_INTEREST_LEDGER_TX_ID;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.RESULT_SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccrueCardInterestSaga — resolve card, post INTEREST ledger tx, skip statement update")
class AccrueCardInterestSagaTest {

    @Mock private CommandBus commandBus;
    @Mock private CardsApi cardsApi;
    @Mock private ExecutionContext ctx;

    private LedgerGlProperties glProperties;
    private AccrueCardInterestSaga saga;

    private static final UUID GL_INTEREST_INCOME = UUID.fromString("00000000-0000-0000-0000-000000000044");
    private static final String SAGA_ID = "saga-interest-001";

    @BeforeEach
    void setUp() {
        glProperties = new LedgerGlProperties();
        glProperties.setInterestIncomeAccountId(GL_INTEREST_INCOME);
        saga = new AccrueCardInterestSaga(commandBus, cardsApi, glProperties);
    }

    @Test
    @DisplayName("postInterestLedger dispatches INTEREST tx with DEBIT cardholder / CREDIT interest-income GL")
    void postInterestLedger_dispatchesInterest() {
        UUID cardId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID ledgerTxId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("12.34");

        when(ctx.getVariableAs(CTX_ACCOUNT_ID, UUID.class)).thenReturn(accountId);
        when(ctx.getVariableAs(CTX_ACCOUNT_CURRENCY, String.class)).thenReturn("EUR");
        when(ctx.getVariableAs(CTX_INTEREST_AMOUNT, BigDecimal.class)).thenReturn(amount);
        when(commandBus.<PostLedgerTransactionResult>send(any(PostLedgerTransactionCommand.class)))
                .thenReturn(Mono.just(PostLedgerTransactionResult.builder().transactionId(ledgerTxId).build()));

        AccrueCardInterestCommand cmd = AccrueCardInterestCommand.builder()
                .cardId(cardId)
                .interestAmount(amount)
                .interestRate(new BigDecimal("19.9"))
                .periodStart(LocalDate.of(2026, 3, 1))
                .periodEnd(LocalDate.of(2026, 3, 31))
                .calculationMethod("DAILY_BALANCE")
                .build();

        StepVerifier.create(saga.postInterestLedger(cmd, SAGA_ID, ctx))
                .assertNext(id -> assertThat(id).isEqualTo(ledgerTxId))
                .verifyComplete();

        ArgumentCaptor<PostLedgerTransactionCommand> cap = ArgumentCaptor.forClass(PostLedgerTransactionCommand.class);
        verify(commandBus).send(cap.capture());
        PostLedgerTransactionCommand dispatched = cap.getValue();
        assertThat(dispatched.getTransactionType()).isEqualTo("INTEREST");
        assertThat(dispatched.getLineType()).isEqualTo("INTEREST");
        assertThat(dispatched.getLineDto()).isInstanceOf(TransactionLineInterestDTO.class);
        TransactionLineInterestDTO line = (TransactionLineInterestDTO) dispatched.getLineDto();
        assertThat(line.getInterestRatePercentage()).isEqualByComparingTo(new BigDecimal("19.9"));
        assertThat(line.getInterestAccrualStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(dispatched.getLegs().get(0).getAccountId()).isEqualTo(accountId);
        assertThat(dispatched.getLegs().get(0).getLegType()).isEqualTo("DEBIT");
        assertThat(dispatched.getLegs().get(1).getAccountId()).isEqualTo(GL_INTEREST_INCOME);
        assertThat(dispatched.getLegs().get(1).getLegType()).isEqualTo("CREDIT");
        verify(ctx).putVariable(CTX_INTEREST_LEDGER_TX_ID, ledgerTxId);
    }

    @Test
    @DisplayName("updateStatement returns skipped sentinel")
    void updateStatement_isSkipped() {
        StepVerifier.create(saga.updateStatement(null, ctx))
                .assertNext(v -> assertThat(v).isEqualTo(RESULT_SKIPPED))
                .verifyComplete();
    }

    @Test
    @DisplayName("resolveCard rejects a card without a linked account")
    void resolveCard_rejectsCardWithoutAccount() {
        UUID cardId = UUID.randomUUID();
        when(cardsApi.getCard(eq(cardId), anyString())).thenReturn(Mono.just(new CardDTO()));

        AccrueCardInterestCommand cmd = AccrueCardInterestCommand.builder()
                .cardId(cardId)
                .interestAmount(new BigDecimal("5.00"))
                .build();

        StepVerifier.create(saga.resolveCard(cmd, ctx))
                .expectErrorMessage("card " + cardId + " has no linked accountId")
                .verify();
    }
}
