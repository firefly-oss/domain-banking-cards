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


package io.fireflysoftware.domain.banking.cards.core.transaction.commands;

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
