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

package io.fireflysoftware.domain.banking.cards.core.card.parameters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CardJourneyParameterConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "firefly.cards.parameters",
            name = "adapter",
            havingValue = "sandbox",
            matchIfMissing = true)
    public CardJourneyParameterPort sandboxCardJourneyParameterPort() {
        return new SandboxCardJourneyParameterAdapter();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "firefly.cards.parameters",
            name = "adapter",
            havingValue = "configuration")
    public CardJourneyParameterPort configurationCardJourneyParameterPort(
            @Value("${firefly.cards.parameters.configuration-base-url:http://localhost:8088}") String baseUrl) {
        return new ConfigurationCardJourneyParameterAdapter(WebClient.builder().baseUrl(baseUrl).build());
    }
}
