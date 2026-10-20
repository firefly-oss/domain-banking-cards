package io.fireflysoftware.domain.banking.cards.core.security.handlers;

import io.fireflysoftware.core.banking.cards.sdk.api.CardSecurityApi;
import io.fireflysoftware.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class UpdateSecuritySettingsHandler extends CommandHandler<UpdateSecuritySettingsCommand, Void> {

    private final CardSecurityApi cardSecurityApi;

    public UpdateSecuritySettingsHandler(CardSecurityApi cardSecurityApi) {
        this.cardSecurityApi = cardSecurityApi;
    }

    @Override
    protected Mono<Void> doHandle(UpdateSecuritySettingsCommand cmd) {
        String correlationId = UUID.randomUUID().toString();

        return cardSecurityApi.getSecuritySetting(cmd.getCardId(), cmd.getSecuritySettingId(), correlationId)
                .flatMap(setting -> {
                    setting.setSecurityStatus(cmd.isEnabled());
                    return cardSecurityApi.updateSecuritySetting(
                            cmd.getCardId(), cmd.getSecuritySettingId(), setting, correlationId);
                })
                .then();
    }
}
