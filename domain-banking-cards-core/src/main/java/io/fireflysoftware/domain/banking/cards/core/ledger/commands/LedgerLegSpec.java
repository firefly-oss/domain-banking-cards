/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.banking.cards.core.ledger.commands;

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
