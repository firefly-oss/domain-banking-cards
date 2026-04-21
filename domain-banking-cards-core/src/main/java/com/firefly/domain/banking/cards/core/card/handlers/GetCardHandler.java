package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.domain.banking.cards.core.card.queries.GetCardQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardHandler extends QueryHandler<GetCardQuery, CardDTO> {

    private final CardsApi cardsApi;

    @Override
    protected Mono<CardDTO> doHandle(GetCardQuery query) {
        return cardsApi.getCard(query.getCardId(), UUID.randomUUID().toString());
    }
}
