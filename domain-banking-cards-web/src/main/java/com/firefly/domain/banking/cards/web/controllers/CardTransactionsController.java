package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.transaction.commands.AuthorizeCardTransactionCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ClearCardTransactionCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.AccrueCardInterestCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ChargeCardFeeCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.PostCardStatementPaymentCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ResolveCardDisputeCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ReverseCardAuthorizationCommand;
import com.firefly.domain.banking.cards.core.transaction.services.CardTransactionSagaService;
import com.firefly.domain.banking.cards.interfaces.dtos.CardTransactionSagaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_CREATE_CARD_TX;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_PLACE_LEDGER_HOLD;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_POST_CLEARING_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_POST_INTEREST_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_POST_FEE_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_CREATE_CARD_PAYMENT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_POST_PAYMENT_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_POST_CHARGEBACK_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_POST_REVERSAL_LEDGER;

/**
 * HTTP entry points for card-transaction sagas (authorization, clearing, reversal,
 * dispute resolution, statement payment, interest accrual, fee charging). Each endpoint
 * dispatches the matching command through {@link CardTransactionSagaService} and shapes the
 * saga outcome into a transport-friendly {@link CardTransactionSagaResponse}.
 */
@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Card Transactions",
        description = "Card-transaction sagas — authorization, clearing, reversal, disputes, "
                + "statement payments, interest accrual, and fees")
public class CardTransactionsController {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final CardTransactionSagaService cardTransactionSagaService;

    @PostMapping("/{cardId}/authorizations")
    @Operation(
            summary = "Authorize a card transaction",
            description = "Creates a PENDING card_transaction, places a PENDING ledger hold on the underlying "
                    + "account, and updates the pending-authorizations projection on the card balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authorization accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid authorization request"),
            @ApiResponse(responseCode = "409", description = "Duplicate authorization reference")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> authorizeCardTransaction(
            @PathVariable UUID cardId,
            @Valid @RequestBody AuthorizeCardTransactionCommand command) {
        AuthorizeCardTransactionCommand bound = withCardId(command, cardId);
        return cardTransactionSagaService.authorizeCardTransaction(bound)
                .map(result -> toResponseEntity(result, toAuthorizationResponse(cardId, result)));
    }

    private AuthorizeCardTransactionCommand withCardId(AuthorizeCardTransactionCommand command, UUID cardId) {
        command.setCardId(cardId);
        return command;
    }

    @PostMapping("/{cardId}/transactions/{transactionId}/clearing")
    @Operation(
            summary = "Clear a card transaction",
            description = "Finalizes a previously-authorized card transaction — transitions the PENDING "
                    + "ledger hold to POSTED (or REVERSAL + new POSTED when the cleared amount differs), "
                    + "marks card_transaction as COMPLETED, and refreshes the card-balance projection.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Clearing processed"),
            @ApiResponse(responseCode = "404", description = "Card transaction or authorization not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate clearing reference")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> clearCardTransaction(
            @PathVariable UUID cardId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody ClearCardTransactionCommand command) {
        command.setCardId(cardId);
        command.setCardTransactionId(transactionId);
        return cardTransactionSagaService.clearCardTransaction(command)
                .map(result -> toResponseEntity(result, toClearingResponse(cardId, transactionId, result)));
    }

    private CardTransactionSagaResponse toClearingResponse(UUID cardId, UUID cardTransactionId, SagaResult result) {
        UUID ledgerTxId = result.resultOf(STEP_POST_CLEARING_LEDGER, UUID.class).orElse(null);
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .cardTransactionId(cardTransactionId)
                .ledgerTransactionId(ledgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    @PostMapping("/{cardId}/transactions/{transactionId}/reversal")
    @Operation(
            summary = "Reverse a card authorization",
            description = "Voids a pending card authorization — posts a REVERSAL against the PENDING "
                    + "ledger hold, marks the card_transaction as REVERSED, and refreshes the balance.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reversal accepted"),
            @ApiResponse(responseCode = "404", description = "Card transaction or authorization not found")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> reverseCardAuthorization(
            @PathVariable UUID cardId,
            @PathVariable UUID transactionId,
            @Valid @RequestBody ReverseCardAuthorizationCommand command) {
        command.setCardId(cardId);
        command.setCardTransactionId(transactionId);
        return cardTransactionSagaService.reverseCardAuthorization(command)
                .map(result -> toResponseEntity(result, toReversalResponse(cardId, transactionId, result)));
    }

    private CardTransactionSagaResponse toReversalResponse(UUID cardId, UUID cardTransactionId, SagaResult result) {
        UUID reversalLedgerTxId = result.resultOf(STEP_POST_REVERSAL_LEDGER, UUID.class).orElse(null);
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .cardTransactionId(cardTransactionId)
                .ledgerTransactionId(reversalLedgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    private CardTransactionSagaResponse toAuthorizationResponse(UUID cardId, SagaResult result) {
        UUID cardTransactionId = result.resultOf(STEP_CREATE_CARD_TX, UUID.class).orElse(null);
        UUID ledgerTxId = result.resultOf(STEP_PLACE_LEDGER_HOLD, UUID.class).orElse(null);
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .cardTransactionId(cardTransactionId)
                .ledgerTransactionId(ledgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    @PostMapping("/{cardId}/disputes/{disputeId}/resolution")
    @Operation(
            summary = "Resolve a card dispute",
            description = "Posts the chargeback ledger transaction (credit cardholder, debit merchant "
                    + "settlement GL), updates the dispute status to RESOLVED, and refreshes the "
                    + "card balance. Supports APPROVED_CARDHOLDER / APPROVED_MERCHANT / SPLIT outcomes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute resolved"),
            @ApiResponse(responseCode = "404", description = "Dispute not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate resolution reference")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> resolveCardDispute(
            @PathVariable UUID cardId,
            @PathVariable UUID disputeId,
            @Valid @RequestBody ResolveCardDisputeCommand command) {
        command.setCardId(cardId);
        command.setDisputeId(disputeId);
        return cardTransactionSagaService.resolveCardDispute(command)
                .map(result -> toResponseEntity(result, toDisputeResponse(cardId, result)));
    }

    private CardTransactionSagaResponse toDisputeResponse(UUID cardId, SagaResult result) {
        Object chargebackResult = result.resultOf(STEP_POST_CHARGEBACK_LEDGER, Object.class).orElse(null);
        UUID chargebackLedgerTxId = chargebackResult instanceof UUID uuid ? uuid : null;
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .ledgerTransactionId(chargebackLedgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    @PostMapping("/{cardId}/statements/{statementId}/payments")
    @Operation(
            summary = "Post a credit-card statement payment",
            description = "Creates a PENDING card payment, posts a TRANSFER ledger transaction "
                    + "(DEBIT funding account, CREDIT credit-card receivable GL), marks the payment "
                    + "as COMPLETED, and refreshes the card balance projection.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request"),
            @ApiResponse(responseCode = "409", description = "Duplicate payment reference")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> postCardStatementPayment(
            @PathVariable UUID cardId,
            @PathVariable UUID statementId,
            @Valid @RequestBody PostCardStatementPaymentCommand command) {
        command.setCardId(cardId);
        command.setStatementId(statementId);
        return cardTransactionSagaService.postCardStatementPayment(command)
                .map(result -> toResponseEntity(result, toStatementPaymentResponse(cardId, result)));
    }

    private CardTransactionSagaResponse toStatementPaymentResponse(UUID cardId, SagaResult result) {
        UUID cardPaymentId = result.resultOf(STEP_CREATE_CARD_PAYMENT, UUID.class).orElse(null);
        UUID ledgerTxId = result.resultOf(STEP_POST_PAYMENT_LEDGER, UUID.class).orElse(null);
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .cardTransactionId(cardPaymentId)
                .ledgerTransactionId(ledgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    @PostMapping("/{cardId}/interest-accrual")
    @Operation(
            summary = "Accrue interest on a credit-card statement",
            description = "Posts an INTEREST ledger transaction — DEBIT cardholder account / "
                    + "CREDIT interest-income GL — for a single statement period. Typically invoked "
                    + "by a batch/scheduler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Interest accrual posted"),
            @ApiResponse(responseCode = "409", description = "Duplicate accrual for the same saga")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> accrueCardInterest(
            @PathVariable UUID cardId,
            @Valid @RequestBody AccrueCardInterestCommand command) {
        command.setCardId(cardId);
        return cardTransactionSagaService.accrueCardInterest(command)
                .map(result -> toResponseEntity(result, toInterestResponse(cardId, result)));
    }

    private CardTransactionSagaResponse toInterestResponse(UUID cardId, SagaResult result) {
        UUID ledgerTxId = result.resultOf(STEP_POST_INTEREST_LEDGER, UUID.class).orElse(null);
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .ledgerTransactionId(ledgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    @PostMapping("/{cardId}/fees")
    @Operation(
            summary = "Charge a card fee",
            description = "Posts a FEE ledger transaction (annual, late-payment, cash advance, "
                    + "foreign-transaction). When the fee is waived the ledger post is skipped.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fee processed"),
            @ApiResponse(responseCode = "409", description = "Duplicate fee for the same saga")
    })
    public Mono<ResponseEntity<CardTransactionSagaResponse>> chargeCardFee(
            @PathVariable UUID cardId,
            @Valid @RequestBody ChargeCardFeeCommand command) {
        command.setCardId(cardId);
        return cardTransactionSagaService.chargeCardFee(command)
                .map(result -> toResponseEntity(result, toFeeResponse(cardId, result)));
    }

    private CardTransactionSagaResponse toFeeResponse(UUID cardId, SagaResult result) {
        Object feeResult = result.resultOf(STEP_POST_FEE_LEDGER, Object.class).orElse(null);
        UUID ledgerTxId = feeResult instanceof UUID uuid ? uuid : null;
        return CardTransactionSagaResponse.builder()
                .cardId(cardId)
                .ledgerTransactionId(ledgerTxId)
                .executionId(result.correlationId())
                .status(result.isSuccess() ? STATUS_COMPLETED : STATUS_FAILED)
                .build();
    }

    /**
     * Maps the saga outcome to an HTTP response. Success → 200 OK. Failure → 422 Unprocessable
     * Entity (the request was syntactically valid but the saga could not be completed). The body
     * carries the same payload either way so callers can inspect correlationId/step results.
     */
    private static ResponseEntity<CardTransactionSagaResponse> toResponseEntity(
            SagaResult result, CardTransactionSagaResponse body) {
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(body);
    }
}
