package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

/**
 * Command that drives {@code ReverseCardAuthorizationSaga}. Triggered when an authorization
 * is voided, expired, or cancelled by the cardholder. Posts a REVERSAL against the original
 * PENDING ledger hold and marks the {@code card_transaction} as REVERSED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseCardAuthorizationCommand implements Command<UUID> {

    /** Identifier of the card that owns the authorization being reversed. */
    private UUID cardId;

    /** Identifier of the {@code card_transaction} authorized earlier. */
    private UUID cardTransactionId;

    /** Human-readable reason (VOID, EXPIRED, CUSTOMER_CANCELLED, etc.). */
    private String reason;

    /** Idempotency token issued by the network or issuer for this reversal. */
    private String reversalReference;
}
