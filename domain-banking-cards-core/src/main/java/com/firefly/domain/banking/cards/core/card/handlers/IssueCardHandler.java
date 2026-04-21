package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.domain.banking.cards.core.card.commands.IssueCardCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class IssueCardHandler extends CommandHandler<IssueCardCommand, UUID> {

    private final CardsApi cardsApi;

    public IssueCardHandler(CardsApi cardsApi) {
        this.cardsApi = cardsApi;
    }

    @Override
    protected Mono<UUID> doHandle(IssueCardCommand cmd) {
        CardDTO cardDTO = new CardDTO();
        cardDTO.setPartyId(cmd.getCustomerId());
        cardDTO.setAccountId(cmd.getAccountId());
        cardDTO.setCardTypeId(cmd.getCardProgramId());
        cardDTO.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);

        return cardsApi.createCard(cardDTO, UUID.randomUUID().toString())
                .map(CardDTO::getCardId);
    }
}
