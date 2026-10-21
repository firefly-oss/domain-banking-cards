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

import io.fireflysoftware.core.banking.cards.sdk.api.CardsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.CardDTO;
import io.fireflysoftware.domain.banking.cards.core.card.commands.ReplaceCardCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class ReplaceCardHandler extends CommandHandler<ReplaceCardCommand, UUID> {

    private final CardsApi cardsApi;

    public ReplaceCardHandler(CardsApi cardsApi) {
        this.cardsApi = cardsApi;
    }

    @Override
    protected Mono<UUID> doHandle(ReplaceCardCommand cmd) {
        String correlationId = UUID.randomUUID().toString();
        return cardsApi.getCard(cmd.getOldCardId(), correlationId)
                .flatMap(oldCard -> {
                    CardDTO newCard = new CardDTO();
                    newCard.setPartyId(oldCard.getPartyId());
                    newCard.setAccountId(oldCard.getAccountId());
                    newCard.setCardTypeId(oldCard.getCardTypeId());
                    newCard.setBinId(oldCard.getBinId());
                    newCard.setIssuerId(oldCard.getIssuerId());
                    newCard.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);
                    return cardsApi.createCard(newCard, correlationId);
                })
                .map(CardDTO::getCardId);
    }
}
