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


package io.fireflysoftware.domain.banking.cards.core.transaction.utils;

/**
 * Step identifiers, context keys, event names, and sentinel values for the
 * {@code ChargeCardFeeSaga}.
 */
public final class ChargeCardFeeConstants {

    private ChargeCardFeeConstants() {}

    public static final String SAGA_CHARGE_FEE_NAME = "ChargeCardFeeSaga";

    public static final String STEP_RESOLVE_CARD = "resolveCard";
    public static final String STEP_POST_FEE_LEDGER = "postFeeLedger";
    public static final String STEP_UPDATE_STATEMENT = "updateStatement";

    public static final String COMPENSATE_REVERSE_FEE = "reverseFeeLedger";

    public static final String EVENT_CARD_RESOLVED = "card.fee.card.resolved";
    public static final String EVENT_FEE_POSTED = "card.fee.ledger.posted";
    public static final String EVENT_STATEMENT_UPDATED = "card.fee.statement.updated";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_ACCOUNT_ID = "accountId";
    public static final String CTX_ACCOUNT_CURRENCY = "accountCurrency";
    public static final String CTX_FEE_AMOUNT = "feeAmount";
    public static final String CTX_FEE_WAIVED = "feeWaived";
    public static final String CTX_FEE_LEDGER_TX_ID = "feeLedgerTxId";

    public static final String FEE_TYPE_ANNUAL = "ANNUAL";
    public static final String FEE_TYPE_LATE_PAYMENT = "LATE_PAYMENT";
    public static final String FEE_TYPE_CASH_ADVANCE = "CASH_ADVANCE";
    public static final String FEE_TYPE_FOREIGN_TRANSACTION = "FOREIGN_TRANSACTION";

    public static final String RESULT_SKIPPED = "skipped";
}
