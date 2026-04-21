package com.firefly.domain.banking.cards.core.virtual.handlers;

import com.firefly.core.banking.cards.sdk.api.VirtualCardsApi;
import com.firefly.core.banking.cards.sdk.model.VirtualCardDTO;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class IssueVirtualCardHandler extends CommandHandler<IssueVirtualCardCommand, UUID> {

    private final VirtualCardsApi virtualCardsApi;

    public IssueVirtualCardHandler(VirtualCardsApi virtualCardsApi) {
        this.virtualCardsApi = virtualCardsApi;
    }

    @Override
    protected Mono<UUID> doHandle(IssueVirtualCardCommand cmd) {
        String correlationId = UUID.randomUUID().toString();

        VirtualCardDTO virtualCard = new VirtualCardDTO();
        virtualCard.setCardId(cmd.getParentCardId());
        virtualCard.setVirtualCardStatus(VirtualCardDTO.VirtualCardStatusEnum.ACTIVE);

        return virtualCardsApi.createVirtualCard(cmd.getParentCardId(), virtualCard, correlationId)
                .map(VirtualCardDTO::getVirtualCardId);
    }
}
