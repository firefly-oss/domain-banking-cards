package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command that drives {@code PostCardStatementPaymentSaga}. A cardholder pays the outstanding
 * statement balance on a credit card from their checking account; the saga posts a TRANSFER
 * ledger transaction (DEBIT funding account, CREDIT credit-card receivable GL) and records the
 * payment against the card.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCardStatementPaymentCommand implements Command<UUID> {

    private UUID cardId;

    /** Checking or deposit account that funds the payment. */
    private UUID fundingAccountId;

    /** Statement being paid. */
    private UUID statementId;

    /** Amount the cardholder pays — may be partial or full. */
    private BigDecimal paymentAmount;

    /** ISO 4217 currency code. */
    private String currency;

    /** {@code true} when the payment is triggered by an automatic-pay enrollment. */
    private Boolean isAutoPayment;

    /** {@code true} when the payment covers the entire statement balance. */
    private Boolean isFullPayment;

    /** Idempotency token — typically the channel reference for the payment. */
    private String paymentReference;
}
