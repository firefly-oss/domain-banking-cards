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


package io.fireflysoftware.domain.banking.cards.core.security.workflows;

import io.fireflysoftware.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "ManageSecuritySettingsSaga")
@Service
public class ManageSecuritySettingsSaga {

    private final CommandBus commandBus;

    public ManageSecuritySettingsSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateSecurityChange")
    @StepEvent(type = "card.security.validated")
    public Mono<Boolean> validateSecurityChange(UpdateSecuritySettingsCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("cardId", cmd.getCardId());
        ctx.putVariable("securitySettingId", cmd.getSecuritySettingId());
        ctx.putVariable("previousEnabled", true);
        return Mono.just(true);
    }

    @SagaStep(id = "updateSecuritySettings", compensate = "revertSecuritySettings", dependsOn = "validateSecurityChange")
    @StepEvent(type = "card.security.updated")
    public Mono<Void> updateSecuritySettings(UpdateSecuritySettingsCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    public Mono<Void> revertSecuritySettings(ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable("cardId");
        UUID securitySettingId = (UUID) ctx.getVariable("securitySettingId");
        Boolean previousEnabled = (Boolean) ctx.getVariable("previousEnabled");

        return commandBus.<Void>send(UpdateSecuritySettingsCommand.builder()
                .cardId(cardId)
                .securitySettingId(securitySettingId)
                .enabled(previousEnabled != null && previousEnabled)
                .build());
    }

    @SagaStep(id = "notifySecurityChange", dependsOn = "updateSecuritySettings")
    @StepEvent(type = "card.security.change.notified")
    public Mono<String> notifySecurityChange(UpdateSecuritySettingsCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
