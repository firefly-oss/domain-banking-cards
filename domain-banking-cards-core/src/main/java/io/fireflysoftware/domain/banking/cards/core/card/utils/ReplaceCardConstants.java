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

public final class ReplaceCardConstants {

    private ReplaceCardConstants() {}

    public static final String SAGA_REPLACE_CARD_NAME = "ReplaceCardSaga";

    public static final String STEP_VALIDATE_OLD_CARD = "validateOldCard";
    public static final String STEP_CREATE_REPLACEMENT = "createReplacementCard";
    public static final String STEP_TRANSFER_LIMITS = "transferLimits";
    public static final String STEP_TRANSFER_SECURITY = "transferSecuritySettings";
    public static final String STEP_CANCEL_OLD_CARD = "cancelOldCard";
    public static final String STEP_ORDER_PHYSICAL = "orderPhysicalReplacement";

    public static final String COMPENSATE_DELETE_NEW_CARD = "deleteNewCard";
    public static final String COMPENSATE_RESTORE_OLD_CARD = "restoreOldCard";

    public static final String EVENT_OLD_CARD_VALIDATED = "card.old.validated";
    public static final String EVENT_REPLACEMENT_CREATED = "card.replacement.created";
    public static final String EVENT_LIMITS_TRANSFERRED = "card.limits.transferred";
    public static final String EVENT_SECURITY_TRANSFERRED = "card.security.transferred";
    public static final String EVENT_OLD_CARD_CANCELLED = "card.old.cancelled";
    public static final String EVENT_PHYSICAL_ORDERED = "card.physical.ordered";

    public static final String CTX_OLD_CARD_ID = "oldCardId";
    public static final String CTX_NEW_CARD_ID = "newCardId";
    public static final String CTX_OLD_CARD_BACKUP = "oldCardBackup";
}
