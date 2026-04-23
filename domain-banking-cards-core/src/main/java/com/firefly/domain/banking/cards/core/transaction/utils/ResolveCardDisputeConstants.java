package com.firefly.domain.banking.cards.core.transaction.utils;

/**
 * Step identifiers, context keys, event names, and sentinel values for the
 * {@code ResolveCardDisputeSaga}.
 */
public final class ResolveCardDisputeConstants {

    private ResolveCardDisputeConstants() {}

    public static final String SAGA_RESOLVE_DISPUTE_NAME = "ResolveCardDisputeSaga";

    public static final String STEP_LOAD_DISPUTE = "loadDispute";
    public static final String STEP_POST_CHARGEBACK_LEDGER = "postChargebackLedger";
    public static final String STEP_UPDATE_DISPUTE = "updateDispute";
    public static final String STEP_REFRESH_CARD_BALANCE = "refreshCardBalance";

    public static final String COMPENSATE_ROLLBACK_DISPUTE = "rollbackDisputeStatus";
    public static final String COMPENSATE_REVERSE_CHARGEBACK = "reverseChargeback";

    public static final String EVENT_DISPUTE_LOADED = "card.dispute.loaded";
    public static final String EVENT_CHARGEBACK_POSTED = "card.dispute.chargeback.posted";
    public static final String EVENT_DISPUTE_UPDATED = "card.dispute.updated";
    public static final String EVENT_CARD_BALANCE_REFRESHED = "card.dispute.card-balance.refreshed";

    public static final String CTX_DISPUTE_ID = "disputeId";
    public static final String CTX_CARD_ID = "cardId";
    public static final String CTX_ACCOUNT_ID = "accountId";
    public static final String CTX_CARD_TRANSACTION_ID = "cardTransactionId";
    public static final String CTX_ACCOUNT_CURRENCY = "accountCurrency";
    public static final String CTX_CREDIT_AMOUNT = "creditAmount";
    public static final String CTX_DEBIT_AMOUNT = "debitAmount";
    public static final String CTX_RESOLUTION_OUTCOME = "resolutionOutcome";
    public static final String CTX_RESOLUTION_REFERENCE = "resolutionReference";
    public static final String CTX_ORIGINAL_LEDGER_TX_ID = "originalLedgerTxId";
    public static final String CTX_CHARGEBACK_LEDGER_TX_ID = "chargebackLedgerTxId";
    public static final String CTX_PREVIOUS_DISPUTE_STATUS = "previousDisputeStatus";

    public static final String OUTCOME_APPROVED_CARDHOLDER = "APPROVED_CARDHOLDER";
    public static final String OUTCOME_APPROVED_MERCHANT = "APPROVED_MERCHANT";
    public static final String OUTCOME_SPLIT = "SPLIT";

    public static final String DISPUTE_STATUS_RESOLVED = "RESOLVED";

    public static final String RESULT_SKIPPED = "skipped";
}
