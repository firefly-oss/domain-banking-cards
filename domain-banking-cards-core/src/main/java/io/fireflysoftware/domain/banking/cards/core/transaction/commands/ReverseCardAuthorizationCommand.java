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
