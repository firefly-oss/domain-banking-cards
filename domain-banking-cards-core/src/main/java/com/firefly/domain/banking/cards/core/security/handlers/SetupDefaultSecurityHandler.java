package com.firefly.domain.banking.cards.core.security.handlers;

import com.firefly.core.banking.cards.sdk.api.CardSecurityApi;
import com.firefly.core.banking.cards.sdk.model.CardSecurityDTO;
import com.firefly.domain.banking.cards.core.security.commands.SetupDefaultSecurityCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class SetupDefaultSecurityHandler extends CommandHandler<SetupDefaultSecurityCommand, Void> {

    private final CardSecurityApi cardSecurityApi;

    public SetupDefaultSecurityHandler(CardSecurityApi cardSecurityApi) {
        this.cardSecurityApi = cardSecurityApi;
    }

    @Override
    protected Mono<Void> doHandle(SetupDefaultSecurityCommand cmd) {
        String correlationId = UUID.randomUUID().toString();

        CardSecurityDTO securityDTO = new CardSecurityDTO();
        securityDTO.setCardId(cmd.getCardId());
        securityDTO.setSecurityFeature(CardSecurityDTO.SecurityFeatureEnum.PIN_ENABLED);
        securityDTO.setSecurityStatus(true);

        return cardSecurityApi.createSecuritySetting(cmd.getCardId(), securityDTO, correlationId)
                .then();
    }
}
