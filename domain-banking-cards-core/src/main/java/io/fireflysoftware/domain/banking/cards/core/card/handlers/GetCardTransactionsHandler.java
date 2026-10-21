/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.banking.cards.core.card.handlers;

import io.fireflysoftware.core.banking.cards.sdk.api.CardTransactionsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.PaginationResponse;
import io.fireflysoftware.domain.banking.cards.core.card.queries.GetCardTransactionsQuery;
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
