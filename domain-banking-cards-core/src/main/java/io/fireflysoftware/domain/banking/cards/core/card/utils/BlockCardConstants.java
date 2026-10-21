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


package io.fireflysoftware.domain.banking.cards.core.card.utils;

public final class BlockCardConstants {

    private BlockCardConstants() {}

    public static final String SAGA_BLOCK_CARD_NAME = "BlockCardSaga";

    public static final String STEP_VALIDATE_CARD = "validateCard";
    public static final String STEP_BLOCK_CARD = "blockCard";
    public static final String STEP_NOTIFY_CUSTOMER = "notifyCustomer";

    public static final String COMPENSATE_UNBLOCK_CARD = "unblockCard";

    public static final String EVENT_CARD_VALIDATED = "card.validated";
    public static final String EVENT_CARD_BLOCKED = "card.blocked";
    public static final String EVENT_CUSTOMER_NOTIFIED = "card.block.notified";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_PREVIOUS_STATUS = "previousStatus";
}
