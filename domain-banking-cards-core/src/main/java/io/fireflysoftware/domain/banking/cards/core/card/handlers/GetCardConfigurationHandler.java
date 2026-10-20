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
