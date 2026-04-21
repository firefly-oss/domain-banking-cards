package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.domain.banking.cards.core.card.commands.UnblockCardCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class UnblockCardHandler extends CommandHandler<UnblockCardCommand, Void> {

    private final CardsApi cardsApi;

    public UnblockCardHandler(CardsApi cardsApi) {
        this.cardsApi = cardsApi;
    }

    @Override
    protected Mono<Void> doHandle(UnblockCardCommand cmd) {
        return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                .flatMap(card -> {
                    card.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);
                    return cardsApi.updateCard(cmd.getCardId(), card, UUID.randomUUID().toString());
                })
                .then();
    }
}
