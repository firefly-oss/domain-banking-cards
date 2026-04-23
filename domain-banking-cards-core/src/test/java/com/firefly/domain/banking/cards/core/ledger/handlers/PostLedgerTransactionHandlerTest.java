package com.firefly.domain.banking.cards.core.ledger.handlers;

import com.firefly.core.banking.ledger.sdk.api.TransactionLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineCardApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineFeeApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineInterestApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineTransferApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionStatusHistoryApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLegDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineCardDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineFeeDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cards PostLedgerTransactionHandler Tests")
class PostLedgerTransactionHandlerTest {

    @Mock
    private TransactionsApi transactionsApi;
    @Mock
    private TransactionLegsApi transactionLegsApi;
    @Mock
    private TransactionStatusHistoryApi transactionStatusHistoryApi;
    @Mock
    private TransactionLineCardApi transactionLineCardApi;
    @Mock
    private TransactionLineFeeApi transactionLineFeeApi;
    @Mock
    private TransactionLineInterestApi transactionLineInterestApi;
    @Mock
    private TransactionLineTransferApi transactionLineTransferApi;

    private PostLedgerTransactionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PostLedgerTransactionHandler(
                transactionsApi,
                transactionLegsApi,
                transactionStatusHistoryApi,
                transactionLineCardApi,
                transactionLineFeeApi,
                transactionLineInterestApi,
                transactionLineTransferApi);
    }

    @Test
    @DisplayName("Happy path: card authorization with PENDING status → skips status transition, posts card line")
    void cardAuthorization_pendingStatus() {
        UUID accountId = UUID.randomUUID();
        UUID glSuspense = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        TransactionLineCardDTO cardLine = new TransactionLineCardDTO();
        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("card-auth:NET-REF-123")
                .transactionType("CARD")
                .totalAmount(amount)
                .currency("EUR")
                .valueDate(LocalDateTime.now())
                .bookingDate(LocalDateTime.now())
                .description("Card authorization")
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType("DEBIT").amount(amount).currency("EUR").build(),
                        LedgerLegSpec.builder().accountId(glSuspense).legType("CREDIT").amount(amount).currency("EUR").build()))
                .lineType("CARD")
                .lineDto(cardLine)
                .initialStatus("PENDING")
                .build();

        when(transactionsApi.createTransaction(any(TransactionDTO.class), anyString()))
                .thenReturn(Mono.just(new TransactionDTO(null, null, txId, null)));
        when(transactionLegsApi.createTransactionLeg(eq(txId), any(TransactionLegDTO.class), anyString()))
                .thenReturn(Mono.just(new TransactionLegDTO()));
        when(transactionLineCardApi.createCardLine(eq(txId), any(TransactionLineCardDTO.class), anyString()))
                .thenReturn(Mono.just(cardLine));

        StepVerifier.create(handler.handle(cmd))
                .assertNext(result -> assertThat(result.getTransactionId()).isEqualTo(txId))
                .verifyComplete();

        verify(transactionsApi).createTransaction(any(TransactionDTO.class), anyString());
        verify(transactionLegsApi, times(2)).createTransactionLeg(eq(txId), any(TransactionLegDTO.class), anyString());
        verify(transactionLineCardApi).createCardLine(eq(txId), any(TransactionLineCardDTO.class), anyString());
        verify(transactionsApi, never()).updateTransactionStatus(any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Fee line with POSTED status → card api untouched, fee line posted, status transitioned")
    void feeLine_postedStatus() {
        UUID accountId = UUID.randomUUID();
        UUID glFeeIncome = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("25.00");

        TransactionLineFeeDTO feeLine = new TransactionLineFeeDTO();
        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("saga-fee:STEP_FEE")
                .transactionType("FEE")
                .totalAmount(amount)
                .currency("EUR")
                .valueDate(LocalDateTime.now())
                .description("Annual card fee")
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType("DEBIT").amount(amount).currency("EUR").build(),
                        LedgerLegSpec.builder().accountId(glFeeIncome).legType("CREDIT").amount(amount).currency("EUR").build()))
                .lineType("FEE")
                .lineDto(feeLine)
                .initialStatus("POSTED")
                .build();

        when(transactionsApi.createTransaction(any(TransactionDTO.class), anyString()))
                .thenReturn(Mono.just(new TransactionDTO(null, null, txId, null)));
        when(transactionLegsApi.createTransactionLeg(eq(txId), any(TransactionLegDTO.class), anyString()))
                .thenReturn(Mono.just(new TransactionLegDTO()));
        when(transactionLineFeeApi.createFeeLine(eq(txId), any(TransactionLineFeeDTO.class), anyString()))
                .thenReturn(Mono.just(feeLine));
        when(transactionsApi.updateTransactionStatus(eq(txId), eq("POSTED"), anyString(), anyString()))
                .thenReturn(Mono.just(new TransactionDTO(null, null, txId, null)));

        StepVerifier.create(handler.handle(cmd))
                .assertNext(result -> assertThat(result.getTransactionId()).isEqualTo(txId))
                .verifyComplete();

        verify(transactionLineFeeApi).createFeeLine(eq(txId), any(TransactionLineFeeDTO.class), anyString());
        verify(transactionLineCardApi, never()).createCardLine(any(), any(), anyString());
    }

    @Test
    @DisplayName("Unbalanced legs → IllegalArgumentException and no SDK interactions")
    void unbalancedLegs_failsFast() {
        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("saga-bad:STEP_UNBALANCED")
                .transactionType("CARD")
                .totalAmount(new BigDecimal("50.00"))
                .currency("EUR")
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(UUID.randomUUID()).legType("DEBIT")
                                .amount(new BigDecimal("50.00")).currency("EUR").build(),
                        LedgerLegSpec.builder().accountId(UUID.randomUUID()).legType("CREDIT")
                                .amount(new BigDecimal("30.00")).currency("EUR").build()))
                .initialStatus("PENDING")
                .build();

        StepVerifier.create(handler.handle(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(IllegalArgumentException.class);
                    assertThat(err.getMessage()).contains("unbalanced legs");
                })
                .verify();

        verifyNoInteractions(transactionsApi, transactionLegsApi,
                transactionLineCardApi, transactionLineFeeApi,
                transactionLineInterestApi, transactionLineTransferApi);
    }

    @Test
    @DisplayName("Chargeback: relatedTransactionId and relationType propagate into TransactionDTO")
    void chargeback_propagatesRelationFields() {
        UUID originalTxId = UUID.randomUUID();
        UUID txId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID merchantSettlement = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("saga-dispute:STEP_CHARGEBACK")
                .transactionType("CARD")
                .totalAmount(amount)
                .currency("EUR")
                .description("Chargeback for dispute")
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(merchantSettlement).legType("DEBIT").amount(amount).currency("EUR").build(),
                        LedgerLegSpec.builder().accountId(accountId).legType("CREDIT").amount(amount).currency("EUR").build()))
                .relatedTransactionId(originalTxId)
                .relationType("CHARGEBACK")
                .initialStatus("POSTED")
                .build();

        when(transactionsApi.createTransaction(any(TransactionDTO.class), anyString()))
                .thenReturn(Mono.just(new TransactionDTO(null, null, txId, null)));
        when(transactionLegsApi.createTransactionLeg(eq(txId), any(TransactionLegDTO.class), anyString()))
                .thenReturn(Mono.just(new TransactionLegDTO()));
        when(transactionsApi.updateTransactionStatus(eq(txId), eq("POSTED"), anyString(), anyString()))
                .thenReturn(Mono.just(new TransactionDTO(null, null, txId, null)));

        ArgumentCaptor<TransactionDTO> dtoCaptor = ArgumentCaptor.forClass(TransactionDTO.class);

        StepVerifier.create(handler.handle(cmd))
                .assertNext(result -> assertThat(result.getTransactionId()).isEqualTo(txId))
                .verifyComplete();

        verify(transactionsApi).createTransaction(dtoCaptor.capture(), anyString());
        TransactionDTO sent = dtoCaptor.getValue();
        assertThat(sent.getRelatedTransactionId()).isEqualTo(originalTxId);
        assertThat(sent.getRelationType()).isEqualTo("CHARGEBACK");
    }

    @Test
    @DisplayName("Duplicate externalReference: ledger error propagates and legs never posted")
    void duplicateExternalReference_propagatesError() {
        UUID accountId = UUID.randomUUID();
        UUID glId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("100.00");

        PostLedgerTransactionCommand cmd = PostLedgerTransactionCommand.builder()
                .externalReference("card-auth:DUPLICATE")
                .transactionType("CARD")
                .totalAmount(amount)
                .currency("EUR")
                .legs(List.of(
                        LedgerLegSpec.builder().accountId(accountId).legType("DEBIT").amount(amount).currency("EUR").build(),
                        LedgerLegSpec.builder().accountId(glId).legType("CREDIT").amount(amount).currency("EUR").build()))
                .initialStatus("PENDING")
                .build();

        when(transactionsApi.createTransaction(any(TransactionDTO.class), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("duplicate externalReference")));

        StepVerifier.create(handler.handle(cmd))
                .expectErrorMatches(err -> err instanceof IllegalStateException
                        && err.getMessage().contains("duplicate"))
                .verify();

        verify(transactionLegsApi, never()).createTransactionLeg(any(), any(), anyString());
    }
}
