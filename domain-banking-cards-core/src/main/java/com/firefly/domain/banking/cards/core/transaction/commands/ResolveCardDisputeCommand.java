package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command that drives {@code ResolveCardDisputeSaga}. Carries the arbiter's resolution
 * outcome plus the amounts to credit the cardholder and debit the merchant (one may be zero
 * depending on {@code resolutionOutcome}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveCardDisputeCommand implements Command<UUID> {

    /** Identifier of the card on which the dispute is filed (path binding). */
    private UUID cardId;

    /** Identifier of the dispute to resolve. */
    private UUID disputeId;

    /** APPROVED_CARDHOLDER | APPROVED_MERCHANT | SPLIT. */
    private String resolutionOutcome;

    /** Amount credited back to the cardholder (0 when the merchant wins). */
    private BigDecimal creditAmount;

    /** Amount debited from the merchant settlement GL (0 when the cardholder wins). */
    private BigDecimal debitAmount;

    /** Network or issuer reference for the resolution — stable idempotency key. */
    private String resolutionReference;
}
