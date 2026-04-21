package com.firefly.domain.banking.cards.core.card.utils;

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
