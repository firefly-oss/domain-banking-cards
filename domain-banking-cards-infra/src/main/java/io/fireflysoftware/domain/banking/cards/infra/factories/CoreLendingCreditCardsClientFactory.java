/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.banking.cards.infra.factories;

import io.fireflysoftware.core.lending.cards.sdk.api.*;
import io.fireflysoftware.core.lending.cards.sdk.invoker.ApiClient;
import io.fireflysoftware.domain.banking.cards.infra.properties.CoreLendingCreditCardsProperties;
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
