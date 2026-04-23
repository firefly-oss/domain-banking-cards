package com.firefly.domain.banking.cards.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for core-banking-ledger API.
 * Maps the properties defined in application.yaml under api-configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "api-configuration.core-platform.banking-ledger")
@Data
public class CoreBankingLedgerProperties {
    private String basePath;
}
