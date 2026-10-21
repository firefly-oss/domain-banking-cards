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
