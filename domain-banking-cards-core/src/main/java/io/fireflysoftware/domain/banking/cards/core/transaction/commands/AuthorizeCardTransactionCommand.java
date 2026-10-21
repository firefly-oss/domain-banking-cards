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
