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


package io.fireflysoftware.domain.banking.cards.core.card.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fireflysoftware.core.banking.cards.sdk.api.CardConfigurationsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.CardConfigurationDTO;
import io.fireflysoftware.domain.banking.cards.core.card.queries.GetCardConfigurationQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardConfigurationHandler extends QueryHandler<GetCardConfigurationQuery, CardConfigurationDTO> {

    private final CardConfigurationsApi cardConfigurationsApi;
    private final ObjectMapper objectMapper;

    @Override
    protected Mono<CardConfigurationDTO> doHandle(GetCardConfigurationQuery query) {
        return cardConfigurationsApi.getAllConfigurations(
                        query.getCardId(),
                        null, null, null, null,
                        UUID.randomUUID().toString()
                )
                .flatMap(response -> {
                    if (response.getContent() != null && !response.getContent().isEmpty()) {
                        Object first = response.getContent().get(0);
                        CardConfigurationDTO config = objectMapper.convertValue(first, CardConfigurationDTO.class);
                        return Mono.just(config);
                    }
                    return Mono.empty();
                });
    }
}
