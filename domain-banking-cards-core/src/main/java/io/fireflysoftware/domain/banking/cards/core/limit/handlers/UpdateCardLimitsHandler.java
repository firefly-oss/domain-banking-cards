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


package io.fireflysoftware.domain.banking.cards.core.limit.handlers;

import io.fireflysoftware.core.banking.cards.sdk.api.CardLimitsApi;
import io.fireflysoftware.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class UpdateCardLimitsHandler extends CommandHandler<UpdateCardLimitsCommand, Void> {

    private final CardLimitsApi cardLimitsApi;

    public UpdateCardLimitsHandler(CardLimitsApi cardLimitsApi) {
        this.cardLimitsApi = cardLimitsApi;
    }

    @Override
    protected Mono<Void> doHandle(UpdateCardLimitsCommand cmd) {
        String correlationId = UUID.randomUUID().toString();

        return cardLimitsApi.getLimit(cmd.getCardId(), cmd.getLimitId(), correlationId)
                // Normalise the upstream "missing limit" shape. core-banking-cards currently
                // returns HTTP 500 (not 404) when the limit does not exist, so we treat both
                // 404 and 500 on the read path as a domain-level not-found so the web layer
                // can surface a clean 404 instead of leaking the raw WebClient exception.
                .onErrorMap(UpdateCardLimitsHandler::isNotFoundOrServerError,
                        cause -> new LimitNotFoundException(cmd.getCardId(), cmd.getLimitId(), cause))
                .flatMap(limit -> {
                    if (cmd.getDailyLimit() != null) {
                        limit.setLimitAmount(cmd.getDailyLimit());
                    }
                    return cardLimitsApi.updateLimit(cmd.getCardId(), cmd.getLimitId(), limit, correlationId);
                })
                .then();
    }

    private static boolean isNotFoundOrServerError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wcre) {
            int status = wcre.getStatusCode().value();
            return status == 404 || status == 500;
        }
        return false;
    }
}
