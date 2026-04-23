package com.firefly.domain.banking.cards.core.security.workflows;

import com.firefly.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
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
