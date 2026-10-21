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


package io.fireflysoftware.domain.banking.cards.core.limit.handlers;

import java.util.UUID;

/**
 * Raised by the card-limit handlers when the upstream core rejects the read with a
 * "not found" signal. The current core-banking-cards build returns HTTP 500 for a missing
 * limit instead of 404 — the handler normalises both shapes into this domain-level
 * exception so the web layer can return a clean HTTP 404.
 */
public class LimitNotFoundException extends RuntimeException {

    public LimitNotFoundException(UUID cardId, UUID limitId, Throwable cause) {
        super("card limit not found cardId=" + cardId + " limitId=" + limitId, cause);
    }
}
