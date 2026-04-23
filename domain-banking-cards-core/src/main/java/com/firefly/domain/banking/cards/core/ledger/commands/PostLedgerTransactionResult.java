package com.firefly.domain.banking.cards.core.ledger.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Result of a {@link PostLedgerTransactionCommand} — carries the id of the newly created ledger
 * transaction so saga steps can stash it in their execution context for compensation lookups.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostLedgerTransactionResult {
    private UUID transactionId;
}
