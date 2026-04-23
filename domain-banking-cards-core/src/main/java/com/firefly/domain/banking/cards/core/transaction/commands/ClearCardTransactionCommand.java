package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command that drives {@code ClearCardTransactionSaga}. Carries the clearing-file payload
 * for a previously authorized card transaction: the cleared amount (which may differ from
 * the authorized amount), settlement timestamp, and the network clearing reference used to
 * distinguish the clearing post from the original authorization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClearCardTransactionCommand implements Command<UUID> {

    /** Identifier of the card that owns the transaction being cleared. */
    private UUID cardId;

    /** Identifier of the {@code card_transaction} created during authorization. */
    private UUID cardTransactionId;

    /** Final amount the acquirer clears — may differ from the authorized amount. */
    private BigDecimal clearedAmount;

    /** Timestamp of the settlement record in the clearing file. */
    private LocalDateTime settlementTimestamp;

    /** Network reference for the clearing leg — provides idempotency for clearing retries. */
    private String networkClearingReference;
}
