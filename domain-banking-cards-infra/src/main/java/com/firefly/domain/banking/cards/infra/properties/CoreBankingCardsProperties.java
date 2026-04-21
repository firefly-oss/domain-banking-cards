package com.firefly.domain.banking.cards.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Core Banking Cards API.
 * Maps the properties defined in application.yaml under api-configuration.core-banking.cards.
 */
@Configuration
@ConfigurationProperties(prefix = "api-configuration.core-banking.cards")
@Data
public class CoreBankingCardsProperties {
    private String basePath;
}
