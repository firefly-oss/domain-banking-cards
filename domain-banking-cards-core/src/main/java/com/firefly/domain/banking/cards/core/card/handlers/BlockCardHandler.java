package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.domain.banking.cards.core.card.commands.BlockCardCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class BlockCardHandler extends CommandHandler<BlockCardCommand, Void> {

    private final CardsApi cardsApi;

    public BlockCardHandler(CardsApi cardsApi) {
        this.cardsApi = cardsApi;
    }

    @Override
    protected Mono<Void> doHandle(BlockCardCommand cmd) {
        return cardsApi.getCard(cmd.getCardId(), UUID.randomUUID().toString())
                .flatMap(card -> {
                    card.setCardStatus(CardDTO.CardStatusEnum.BLOCKED);
                    return cardsApi.updateCard(cmd.getCardId(), card, UUID.randomUUID().toString());
                })
                .then();
    }
}
