package com.firefly.domain.banking.cards.core.limit.handlers;

import java.util.UUID;

/**
 * Raised by the card-limit handlers when the upstream core rejects the read with a
 * "not found" signal. The current core-banking-cards build returns HTTP 500 for a missing
 * limit instead of 404 — the handler normalises both shapes into this domain-level
 * exception so the web layer can return a clean HTTP 404.
 */
public class LimitNotFoundException extends RuntimeException {

    public LimitNotFoundException(UUID cardId, UUID limitId, Throwable cause) {
        super("card limit not found cardId=" + cardId + " limitId=" + limitId, cause);
    }
}
