package com.firefly.domain.banking.cards.core.transaction.utils;

/**
 * Step identifiers, context keys, event names, and sentinel values for the
 * {@code PostCardStatementPaymentSaga}.
 */
public final class PostCardStatementPaymentConstants {

    private PostCardStatementPaymentConstants() {}

    public static final String SAGA_POST_STATEMENT_PAYMENT_NAME = "PostCardStatementPaymentSaga";

    public static final String STEP_CREATE_CARD_PAYMENT = "createCardPayment";
    public static final String STEP_POST_PAYMENT_LEDGER = "postPaymentLedger";
    public static final String STEP_UPDATE_CARD_PAYMENT = "updateCardPayment";
    public static final String STEP_UPDATE_STATEMENT = "updateStatement";
    public static final String STEP_REFRESH_CARD_BALANCE = "refreshCardBalance";

    public static final String COMPENSATE_FAIL_PAYMENT = "failCardPayment";
    public static final String COMPENSATE_REVERSE_PAYMENT_LEDGER = "reversePaymentLedger";

    public static final String EVENT_CARD_PAYMENT_CREATED = "card.statement-payment.payment.created";
    public static final String EVENT_PAYMENT_LEDGER_POSTED = "card.statement-payment.ledger.posted";
    public static final String EVENT_CARD_PAYMENT_UPDATED = "card.statement-payment.payment.updated";
    public static final String EVENT_STATEMENT_UPDATED = "card.statement-payment.statement.updated";
    public static final String EVENT_CARD_BALANCE_REFRESHED = "card.statement-payment.card-balance.refreshed";

    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_FUNDING_ACCOUNT_ID = "fundingAccountId";
    public static final String CTX_STATEMENT_ID = "statementId";
    public static final String CTX_PAYMENT_AMOUNT = "paymentAmount";
    public static final String CTX_CURRENCY = "currency";
    public static final String CTX_IS_AUTO = "isAuto";
    public static final String CTX_IS_FULL = "isFull";
    public static final String CTX_PAYMENT_REFERENCE = "paymentReference";
    public static final String CTX_CARD_PAYMENT_ID = "cardPaymentId";
    public static final String CTX_PAYMENT_LEDGER_TX_ID = "paymentLedgerTxId";

    public static final String TRANSFER_PURPOSE_STATEMENT_PAYMENT = "CARD_STATEMENT_PAYMENT";
    public static final String RESULT_SKIPPED = "skipped";
}
