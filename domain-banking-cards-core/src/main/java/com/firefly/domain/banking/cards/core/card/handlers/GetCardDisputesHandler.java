package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardDisputesApi;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.card.queries.GetCardDisputesQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardDisputesHandler extends QueryHandler<GetCardDisputesQuery, PaginationResponse> {

    private final CardDisputesApi cardDisputesApi;

    @Override
    protected Mono<PaginationResponse> doHandle(GetCardDisputesQuery query) {
        return cardDisputesApi.getAllDisputes(
                query.getCardId(),
                null, null, null, null,
                UUID.randomUUID().toString()
        );
    }
}
