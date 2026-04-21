package com.firefly.domain.banking.cards.core.card.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.core.banking.cards.sdk.api.PhysicalCardsApi;
import com.firefly.core.banking.cards.sdk.model.PhysicalCardDTO;
import com.firefly.domain.banking.cards.core.card.queries.GetPhysicalCardQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetPhysicalCardHandler extends QueryHandler<GetPhysicalCardQuery, PhysicalCardDTO> {

    private final PhysicalCardsApi physicalCardsApi;
    private final ObjectMapper objectMapper;

    @Override
    protected Mono<PhysicalCardDTO> doHandle(GetPhysicalCardQuery query) {
        return physicalCardsApi.getAllPhysicalCards(
                        query.getCardId(),
                        null, null, null, null,
                        UUID.randomUUID().toString()
                )
                .flatMap(response -> {
                    if (response.getContent() != null && !response.getContent().isEmpty()) {
                        Object first = response.getContent().get(0);
                        PhysicalCardDTO physicalCard = objectMapper.convertValue(first, PhysicalCardDTO.class);
                        return Mono.just(physicalCard);
                    }
                    return Mono.empty();
                });
    }
}
