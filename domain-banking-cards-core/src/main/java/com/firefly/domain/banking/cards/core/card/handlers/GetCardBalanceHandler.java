package com.firefly.domain.banking.cards.core.card.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.core.banking.cards.sdk.api.CardBalancesApi;
import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import com.firefly.domain.banking.cards.core.card.queries.GetCardBalanceQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardBalanceHandler extends QueryHandler<GetCardBalanceQuery, CardBalanceDTO> {

    private final CardBalancesApi cardBalancesApi;
    private final ObjectMapper objectMapper;

    @Override
    protected Mono<CardBalanceDTO> doHandle(GetCardBalanceQuery query) {
        return cardBalancesApi.getAllBalances(
                        query.getCardId(),
                        null, null, null, null,
                        UUID.randomUUID().toString()
                )
                .flatMap(response -> {
                    if (response.getContent() != null && !response.getContent().isEmpty()) {
                        Object first = response.getContent().get(0);
                        CardBalanceDTO balance = objectMapper.convertValue(first, CardBalanceDTO.class);
                        return Mono.just(balance);
                    }
                    return Mono.empty();
                });
    }
}
