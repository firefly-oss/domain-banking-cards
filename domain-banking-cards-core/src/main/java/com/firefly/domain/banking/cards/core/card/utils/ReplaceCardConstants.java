package com.firefly.domain.banking.cards.core.card.utils;

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
