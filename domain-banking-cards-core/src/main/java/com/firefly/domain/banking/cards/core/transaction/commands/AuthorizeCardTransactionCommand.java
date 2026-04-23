package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command that drives {@code AuthorizeCardTransactionSaga}. Carries the network authorization
 * request payload: amount, merchant data, entry mode, and the network-level reference that
 * provides end-to-end idempotency for retries of the same authorization.
 *
 * <p>The saga returns the {@code card_transaction.id} created in core-banking-cards so the
 * caller can correlate subsequent clearing, reversal, or dispute events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeCardTransactionCommand implements Command<UUID> {

    private UUID cardId;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String merchantCategoryCode;
    private String merchantName;
    private String merchantCity;
    private String merchantCountry;
    private String authorizationCode;
    private String posEntryMode;
    private Boolean cardPresentFlag;
    private BigDecimal currencyConversionRate;

    /**
     * Network-level authorization reference — used as {@code xIdempotencyKey} on the
     * card-transaction create call and as a stable identity suffix on the ledger
     * {@code externalReference} so retries of the same network auth do not double-post.
     */
    private String externalAuthReference;
}
