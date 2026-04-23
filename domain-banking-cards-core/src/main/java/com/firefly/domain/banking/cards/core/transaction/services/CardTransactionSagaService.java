package com.firefly.domain.banking.cards.core.transaction.services;

import com.firefly.domain.banking.cards.core.transaction.commands.AccrueCardInterestCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.AuthorizeCardTransactionCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ChargeCardFeeCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ClearCardTransactionCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.PostCardStatementPaymentCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ResolveCardDisputeCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ReverseCardAuthorizationCommand;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import reactor.core.publisher.Mono;

/**
 * Entry-point service for card-transaction sagas exposed by domain-banking-cards. Controllers
 * call these methods, which launch the corresponding saga via the {@code SagaEngine} and
 * return the raw {@code SagaResult} so the web layer can shape the transport response.
 */
public interface CardTransactionSagaService {

    /**
     * Authorizes a card transaction by creating a PENDING card_transaction row, placing a
     * PENDING ledger hold on the underlying account, and updating the pending-authorizations
     * projection.
     *
     * @param command network-provided authorization request
     * @return the {@link SagaResult} produced by the orchestration engine
     */
    Mono<SagaResult> authorizeCardTransaction(AuthorizeCardTransactionCommand command);

    /**
     * Clears a previously-authorized card transaction, transitioning the PENDING ledger post
     * to POSTED (or posting a REVERSAL + new POSTED when the cleared amount differs from the
     * authorized amount), marking the card_transaction as COMPLETED and refreshing the
     * projected card-balance snapshot.
     *
     * @param command clearing-file payload for the authorized transaction
     * @return the {@link SagaResult} produced by the orchestration engine
     */
    Mono<SagaResult> clearCardTransaction(ClearCardTransactionCommand command);

    /**
     * Reverses a previously authorized card transaction — void, expiration, or cancellation
     * before clearing. Posts a REVERSAL ledger transaction linked to the original PENDING
     * authorization and marks the card_transaction as REVERSED.
     *
     * @param command reversal request for the pending authorization
     * @return the {@link SagaResult} produced by the orchestration engine
     */
    Mono<SagaResult> reverseCardAuthorization(ReverseCardAuthorizationCommand command);

    /**
     * Resolves a card dispute by posting a chargeback ledger transaction (credit to the
     * cardholder, debit to the merchant-settlement GL) linked to the original card-transaction
     * ledger post, and updating the dispute record with the arbiter's outcome flags.
     *
     * @param command resolution outcome and amounts for the dispute
     * @return the {@link SagaResult} produced by the orchestration engine
     */
    Mono<SagaResult> resolveCardDispute(ResolveCardDisputeCommand command);

    /**
     * Posts a credit-card statement payment from a funding account — creates the card payment
     * record, posts the ledger TRANSFER, and updates the card balance projection.
     *
     * @param command statement payment payload
     * @return the {@link SagaResult} produced by the orchestration engine
     */
    Mono<SagaResult> postCardStatementPayment(PostCardStatementPaymentCommand command);

    /** Accrues interest on a credit-card statement — DEBIT cardholder / CREDIT interest-income GL. */
    Mono<SagaResult> accrueCardInterest(AccrueCardInterestCommand command);

    /** Charges a fee on a card (annual, late-payment, cash advance, foreign transaction). */
    Mono<SagaResult> chargeCardFee(ChargeCardFeeCommand command);
}
