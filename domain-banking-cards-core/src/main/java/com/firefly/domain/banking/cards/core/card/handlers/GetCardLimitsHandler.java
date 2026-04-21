package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardLimitsApi;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.card.queries.GetCardLimitsQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardLimitsHandler extends QueryHandler<GetCardLimitsQuery, PaginationResponse> {

    private final CardLimitsApi cardLimitsApi;

    @Override
    protected Mono<PaginationResponse> doHandle(GetCardLimitsQuery query) {
        return cardLimitsApi.getAllLimits(
                query.getCardId(),
                null, null, null, null,
                UUID.randomUUID().toString()
        );
    }
}
