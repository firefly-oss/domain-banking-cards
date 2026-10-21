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


package io.fireflysoftware.domain.banking.cards.interfaces.dtos;

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
