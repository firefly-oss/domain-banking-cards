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
