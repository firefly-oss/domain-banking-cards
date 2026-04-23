package com.firefly.domain.banking.cards.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Identifiers of the general-ledger (GL) "internal" accounts used by domain-banking-cards
 * sagas to post double-entry ledger transactions. These are not customer accounts — they
 * are suspense / clearing / income accounts owned by the bank in core-banking-ledger.
 * Populated from {@code firefly.ledger.gl.*} in application.yaml (or config-server per
 * environment).
 */
@Configuration
@ConfigurationProperties(prefix = "firefly.ledger.gl")
@Data
public class LedgerGlProperties {

    /**
     * GL account credited when a card authorization is placed — holds funds against the
     * customer account while the authorization is pending until clearing or reversal.
     */
    private UUID cardAuthSuspenseAccountId;

    /**
     * GL account debited when a chargeback credits the cardholder — offset of the merchant
     * settlement position that is being reversed.
     */
    private UUID merchantSettlementAccountId;

    /**
     * GL account credited when a cardholder pays their credit-card statement — represents
     * the issuer's receivable from the cardholder for the credit-card program.
     */
    private UUID creditCardReceivableAccountId;

    /** GL account credited when fees are charged to a cardholder. */
    private UUID feeIncomeAccountId;

    /** GL account credited when interest is accrued on a credit-card balance. */
    private UUID interestIncomeAccountId;
}
