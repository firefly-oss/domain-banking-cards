package com.firefly.domain.banking.cards.core.transaction.utils;

/**
 * Step identifiers, context keys, event names, and sentinel values for the
 * {@code ReverseCardAuthorizationSaga}.
 */
public final class ReverseCardAuthorizationConstants {

    private ReverseCardAuthorizationConstants() {}

    public static final String SAGA_REVERSE_CARD_AUTH_NAME = "ReverseCardAuthorizationSaga";

    public static final String STEP_LOOKUP_AUTH = "lookupAuthorization";
    public static final String STEP_POST_REVERSAL_LEDGER = "postReversalLedger";
    public static final String STEP_UPDATE_CARD_TX = "updateCardTransaction";
    public static final String STEP_REFRESH_CARD_BALANCE = "refreshCardBalance";

    public static final String COMPENSATE_LOOKUP_NOOP = "compensateLookupNoop";

    public static final String EVENT_AUTH_LOOKED_UP = "card.reversal.auth.looked-up";
    public static final String EVENT_REVERSAL_POSTED = "card.reversal.ledger.posted";
    public static final String EVENT_CARD_TX_UPDATED = "card.reversal.card-tx.updated";
    public static final String EVENT_CARD_BALANCE_REFRESHED = "card.reversal.card-balance.refreshed";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_ACCOUNT_ID = "accountId";
    public static final String CTX_CARD_TRANSACTION_ID = "cardTransactionId";
    public static final String CTX_ACCOUNT_CURRENCY = "accountCurrency";
    public static final String CTX_REVERSAL_AMOUNT = "reversalAmount";
    public static final String CTX_AUTH_LEDGER_TX_ID = "authLedgerTxId";
    public static final String CTX_REVERSAL_LEDGER_TX_ID = "reversalLedgerTxId";
    public static final String CTX_REASON = "reason";
    public static final String CTX_REVERSAL_REFERENCE = "reversalReference";

    public static final String RESULT_SKIPPED = "skipped";
}
