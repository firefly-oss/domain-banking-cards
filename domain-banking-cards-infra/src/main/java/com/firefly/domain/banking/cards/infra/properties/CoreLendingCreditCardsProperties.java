package com.firefly.domain.banking.cards.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Core Lending Credit Cards API.
 * Maps the properties defined in application.yaml under api-configuration.core-lending.credit-cards.
 */
@Configuration
@ConfigurationProperties(prefix = "api-configuration.core-lending.credit-cards")
@Data
public class CoreLendingCreditCardsProperties {
    private String basePath;
}
