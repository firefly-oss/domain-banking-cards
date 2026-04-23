package com.firefly.domain.banking.cards.interfaces.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Transport-layer response for saga-backed card-transaction operations (authorization,
 * clearing, reversal, dispute resolution, statement payment, interest accrual, fee
 * charging). Captures the saga execution outcome and the identifiers produced by the
 * primary step so downstream systems can correlate subsequent events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardTransactionSagaResponse {

    /** Identifier of the card on which the operation was performed. */
    private UUID cardId;

    /** Identifier of the {@code card_transaction} row created or updated, when applicable. */
    private UUID cardTransactionId;

    /** Identifier of the ledger transaction posted by the saga, when applicable. */
    private UUID ledgerTransactionId;

    /** Correlation identifier of the saga execution. */
    private String executionId;

    /** {@code COMPLETED} if the saga succeeded, {@code FAILED} otherwise. */
    private String status;
}
