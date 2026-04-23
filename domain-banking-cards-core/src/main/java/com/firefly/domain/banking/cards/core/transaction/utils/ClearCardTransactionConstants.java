package com.firefly.domain.banking.cards.core.transaction.utils;

/**
 * Step identifiers, context keys, event names, and sentinel values for the
 * {@code ClearCardTransactionSaga}.
 */
public final class ClearCardTransactionConstants {

    private ClearCardTransactionConstants() {}

    public static final String SAGA_CLEAR_CARD_TX_NAME = "ClearCardTransactionSaga";

    public static final String STEP_LOOKUP_AUTH = "lookupAuthorization";
    public static final String STEP_POST_CLEARING_LEDGER = "postClearingLedger";
    public static final String STEP_UPDATE_CARD_TX = "updateCardTransaction";
    public static final String STEP_REFRESH_CARD_BALANCE = "refreshCardBalance";

    public static final String COMPENSATE_RESTORE_CARD_TX = "restoreCardTransaction";
    public static final String COMPENSATE_REVERSE_CLEARING = "reverseClearingLedger";

    public static final String EVENT_AUTH_LOOKED_UP = "card.clearing.auth.looked-up";
    public static final String EVENT_CLEARING_POSTED = "card.clearing.ledger.posted";
    public static final String EVENT_CARD_TX_UPDATED = "card.clearing.card-tx.updated";
    public static final String EVENT_CARD_BALANCE_REFRESHED = "card.clearing.card-balance.refreshed";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_ACCOUNT_ID = "accountId";
    public static final String CTX_CARD_TRANSACTION_ID = "cardTransactionId";
    public static final String CTX_AUTHORIZED_AMOUNT = "authorizedAmount";
    public static final String CTX_CLEARED_AMOUNT = "clearedAmount";
    public static final String CTX_SETTLEMENT_TIMESTAMP = "settlementTimestamp";
    public static final String CTX_ACCOUNT_CURRENCY = "accountCurrency";
    public static final String CTX_EXTERNAL_AUTH_REFERENCE = "externalAuthReference";
    public static final String CTX_NETWORK_CLEARING_REFERENCE = "networkClearingReference";
    public static final String CTX_AUTH_LEDGER_TX_ID = "authLedgerTxId";
    public static final String CTX_CLEARING_LEDGER_TX_ID = "clearingLedgerTxId";
    public static final String CTX_CLEARING_PATH = "clearingPath";

    public static final String CLEARING_PATH_TRANSITION = "TRANSITION";
    public static final String CLEARING_PATH_REVERSAL_PLUS_NEW = "REVERSAL_PLUS_NEW";

    public static final String RESULT_SKIPPED = "skipped";
}
