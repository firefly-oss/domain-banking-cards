package com.firefly.domain.banking.cards.core.transaction.utils;

/**
 * Step identifiers, context keys, event names, and shared sentinel values for the
 * {@code AuthorizeCardTransactionSaga}.
 */
public final class AuthorizeCardTransactionConstants {

    private AuthorizeCardTransactionConstants() {}

    public static final String SAGA_AUTHORIZE_CARD_TX_NAME = "AuthorizeCardTransactionSaga";

    public static final String STEP_RESOLVE_ACCOUNT = "resolveAccount";
    public static final String STEP_CREATE_CARD_TX = "createCardTransaction";
    public static final String STEP_PLACE_LEDGER_HOLD = "placeLedgerHold";
    public static final String STEP_UPDATE_CARD_BALANCE_PROJECTION = "updateCardBalanceProjection";

    public static final String COMPENSATE_FAIL_CARD_TX = "failCardTransaction";
    public static final String COMPENSATE_REVERSE_LEDGER_HOLD = "reverseLedgerHold";
    public static final String COMPENSATE_REVERT_CARD_BALANCE = "revertCardBalance";

    public static final String EVENT_ACCOUNT_RESOLVED = "card.authorization.account.resolved";
    public static final String EVENT_CARD_TX_CREATED = "card.authorization.card-tx.created";
    public static final String EVENT_LEDGER_HOLD_PLACED = "card.authorization.ledger-hold.placed";
    public static final String EVENT_CARD_BALANCE_PROJECTED = "card.authorization.card-balance.projected";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_ACCOUNT_ID = "accountId";
    public static final String CTX_ACCOUNT_CURRENCY = "accountCurrency";
    public static final String CTX_AMOUNT = "amount";
    public static final String CTX_CURRENCY = "currency";
    public static final String CTX_EXTERNAL_AUTH_REFERENCE = "externalAuthReference";
    public static final String CTX_CARD_TRANSACTION_ID = "cardTransactionId";
    public static final String CTX_LEDGER_TX_ID = "ledgerTxId";
    public static final String CTX_CARD_BALANCE_ID = "cardBalanceId";

    public static final String RESULT_SKIPPED = "skipped";
}
