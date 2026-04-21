package com.firefly.domain.banking.cards.infra.factories;

import com.firefly.core.banking.cards.sdk.api.*;
import com.firefly.core.banking.cards.sdk.invoker.ApiClient;
import com.firefly.domain.banking.cards.infra.properties.CoreBankingCardsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CoreBankingCardsClientFactory {

    private final ApiClient apiClient;

    public CoreBankingCardsClientFactory(CoreBankingCardsProperties properties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(properties.getBasePath());
    }

    @Bean
    public CardsApi cardsApi() {
        return new CardsApi(apiClient);
    }

    @Bean
    public CardBalancesApi cardBalancesApi() {
        return new CardBalancesApi(apiClient);
    }

    @Bean
    public CardLimitsApi cardLimitsApi() {
        return new CardLimitsApi(apiClient);
    }

    @Bean
    public CardSecurityApi cardSecurityApi() {
        return new CardSecurityApi(apiClient);
    }

    @Bean
    public CardConfigurationsApi cardConfigurationsApi() {
        return new CardConfigurationsApi(apiClient);
    }

    @Bean
    public PhysicalCardsApi physicalCardsApi() {
        return new PhysicalCardsApi(apiClient);
    }

    @Bean
    public VirtualCardsApi virtualCardsApi() {
        return new VirtualCardsApi(apiClient);
    }

    @Bean
    public CardTransactionsApi cardTransactionsApi() {
        return new CardTransactionsApi(apiClient);
    }

    @Bean
    public CardPaymentsApi cardPaymentsApi() {
        return new CardPaymentsApi(apiClient);
    }

    @Bean
    public CardEnrollmentsApi cardEnrollmentsApi() {
        return new CardEnrollmentsApi(apiClient);
    }

    @Bean
    public CardActivitiesApi cardActivitiesApi() {
        return new CardActivitiesApi(apiClient);
    }

    @Bean
    public CardDisputesApi cardDisputesApi() {
        return new CardDisputesApi(apiClient);
    }

    @Bean
    public CardProgramsApi cardProgramsApi() {
        return new CardProgramsApi(apiClient);
    }

    @Bean
    public CardInterestsApi cardInterestsApi() {
        return new CardInterestsApi(apiClient);
    }

    @Bean
    public CardPromotionsApi cardPromotionsApi() {
        return new CardPromotionsApi(apiClient);
    }
}
