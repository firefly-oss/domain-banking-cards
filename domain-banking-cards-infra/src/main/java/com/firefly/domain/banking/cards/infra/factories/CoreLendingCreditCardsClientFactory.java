package com.firefly.domain.banking.cards.infra.factories;

import com.firefly.core.lending.cards.sdk.api.*;
import com.firefly.core.lending.cards.sdk.invoker.ApiClient;
import com.firefly.domain.banking.cards.infra.properties.CoreLendingCreditCardsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreLendingCreditCardsClientFactory {

    private final ApiClient apiClient;

    public CoreLendingCreditCardsClientFactory(CoreLendingCreditCardsProperties properties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(properties.getBasePath());
    }

    @Bean
    public CcRevolvingLineApi ccRevolvingLineApi() {
        return new CcRevolvingLineApi(apiClient);
    }

    @Bean
    public CcBillingCycleApi ccBillingCycleApi() {
        return new CcBillingCycleApi(apiClient);
    }

    @Bean
    public CcStatementApi ccStatementApi() {
        return new CcStatementApi(apiClient);
    }

    @Bean
    public CcTransactionApi ccTransactionApi() {
        return new CcTransactionApi(apiClient);
    }

    @Bean
    public CcPaymentApi ccPaymentApi() {
        return new CcPaymentApi(apiClient);
    }

    @Bean
    public CcServicingAgreementApi ccServicingAgreementApi() {
        return new CcServicingAgreementApi(apiClient);
    }
}
