package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardActivitiesApi;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.card.queries.GetCardActivityQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardActivityHandler extends QueryHandler<GetCardActivityQuery, PaginationResponse> {

    private final CardActivitiesApi cardActivitiesApi;

    @Override
    protected Mono<PaginationResponse> doHandle(GetCardActivityQuery query) {
        return cardActivitiesApi.getAllActivities(
                query.getCardId(),
                null, null, null, null,
                UUID.randomUUID().toString()
        );
    }
}
