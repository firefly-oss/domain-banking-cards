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

public final class IssueCardConstants {

    private IssueCardConstants() {}

    public static final String SAGA_ISSUE_CARD_NAME = "IssueCardSaga";

    public static final String STEP_VALIDATE_CUSTOMER = "validateCustomer";
    public static final String STEP_CREATE_CARD = "createCard";
    public static final String STEP_SETUP_LIMITS = "setupDefaultLimits";
    public static final String STEP_SETUP_SECURITY = "setupSecuritySettings";
    public static final String STEP_ORDER_PHYSICAL = "orderPhysicalCard";

    public static final String COMPENSATE_DELETE_CARD = "deleteCard";
    public static final String COMPENSATE_REMOVE_LIMITS = "removeLimits";
    public static final String COMPENSATE_REMOVE_SECURITY = "removeSecuritySettings";
    public static final String COMPENSATE_CANCEL_ORDER = "cancelPhysicalCardOrder";

    public static final String EVENT_CUSTOMER_VALIDATED = "card.customer.validated";
    public static final String EVENT_CARD_CREATED = "card.created";
    public static final String EVENT_LIMITS_SET = "card.limits.set";
    public static final String EVENT_SECURITY_SET = "card.security.set";
    public static final String EVENT_PHYSICAL_ORDERED = "card.physical.ordered";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_CUSTOMER_ID = "customerId";
    public static final String CTX_PHYSICAL_CARD_ID = "physicalCardId";
}
