package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.VirtualCardsApi;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.card.queries.GetVirtualCardsQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetVirtualCardsHandler extends QueryHandler<GetVirtualCardsQuery, PaginationResponse> {

    private final VirtualCardsApi virtualCardsApi;

    @Override
    protected Mono<PaginationResponse> doHandle(GetVirtualCardsQuery query) {
        return virtualCardsApi.getAllVirtualCards(
                query.getCardId(),
                null, null, null, null,
                UUID.randomUUID().toString()
        );
    }
}
