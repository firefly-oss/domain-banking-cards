package com.firefly.domain.banking.cards.core.ledger.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Specification for a single double-entry ledger leg — either DEBIT or CREDIT — posted against
 * a given account (and optionally a specific account space). Every {@link PostLedgerTransactionCommand}
 * carries a list of these specs, and the handler validates that Σ DEBIT amounts equals Σ CREDIT
 * amounts before creating the transaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerLegSpec {
    private UUID accountId;
    private UUID accountSpaceId;
    private String legType;
    private BigDecimal amount;
    private String currency;
}
