package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardTransactionsApi;
import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import com.firefly.domain.banking.cards.core.card.queries.GetCardTransactionsQuery;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

@QueryHandlerComponent
@RequiredArgsConstructor
public class GetCardTransactionsHandler extends QueryHandler<GetCardTransactionsQuery, PaginationResponse> {

    private final CardTransactionsApi cardTransactionsApi;

    @Override
    protected Mono<PaginationResponse> doHandle(GetCardTransactionsQuery query) {
        return cardTransactionsApi.getAllTransactions(
                query.getCardId(),
                null,
                null,
                UUID.randomUUID().toString()
        );
    }
}
