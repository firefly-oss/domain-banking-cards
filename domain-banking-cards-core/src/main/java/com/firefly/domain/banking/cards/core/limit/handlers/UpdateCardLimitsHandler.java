package com.firefly.domain.banking.cards.core.limit.handlers;

import com.firefly.core.banking.cards.sdk.api.CardLimitsApi;
import com.firefly.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
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
                .flatMap(limit -> {
                    if (cmd.getDailyLimit() != null) {
                        limit.setLimitAmount(cmd.getDailyLimit());
                    }
                    return cardLimitsApi.updateLimit(cmd.getCardId(), cmd.getLimitId(), limit, correlationId);
                })
                .then();
    }
}
