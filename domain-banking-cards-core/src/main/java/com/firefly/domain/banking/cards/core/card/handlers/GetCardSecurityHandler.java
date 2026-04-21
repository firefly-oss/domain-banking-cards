package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardSecurityApi;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.card.queries.GetCardSecurityQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardSecurityHandler extends QueryHandler<GetCardSecurityQuery, PaginationResponse> {

    private final CardSecurityApi cardSecurityApi;

    @Override
    protected Mono<PaginationResponse> doHandle(GetCardSecurityQuery query) {
        return cardSecurityApi.getAllSecuritySettings(
                query.getCardId(),
                null, null, null, null,
                UUID.randomUUID().toString()
        );
    }
}
