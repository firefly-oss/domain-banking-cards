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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Reads card issuance defaults from domain-core-configuration.
 * An installer selects this class in place of {@link SandboxCardJourneyParameterAdapter}.
 */
public final class ConfigurationCardJourneyParameterAdapter implements CardJourneyParameterPort {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public ConfigurationCardJourneyParameterAdapter(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<CardIssuanceDefaults> issuanceDefaults() {
        return read(SandboxCardJourneyParameterAdapter.DEFAULT_CURRENCY_KEY)
                .zipWith(read(SandboxCardJourneyParameterAdapter.TENURE_YEARS_KEY))
                .map(pair -> new CardIssuanceDefaults(pair.getT1(), Integer.parseInt(pair.getT2())));
    }

    private Mono<String> read(String key) {
        return webClient.get()
                .uri("/api/v1/parameters/by-key/{key}", key)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::value);
    }

    private String value(String json) {
        try {
            return mapper.readTree(json).path("value").asText();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Configuration response was not JSON");
        }
    }
}
