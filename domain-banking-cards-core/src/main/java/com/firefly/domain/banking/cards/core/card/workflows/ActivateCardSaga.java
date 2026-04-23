package com.firefly.domain.banking.cards.core.card.workflows;

import com.firefly.domain.banking.cards.core.card.commands.ActivateCardCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "ActivateCardSaga")
@Service
public class ActivateCardSaga {

    private final CommandBus commandBus;

    public ActivateCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateActivationCode")
    @StepEvent(type = "card.activation.code.validated")
    public Mono<Boolean> validateActivationCode(ActivateCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("cardId", cmd.getCardId());
        return Mono.just(true);
    }

    @SagaStep(id = "activateCard", compensate = "deactivateCard", dependsOn = "validateActivationCode")
    @StepEvent(type = "card.activated")
    public Mono<Void> activateCard(ActivateCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    public Mono<Void> deactivateCard(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = "sendWelcomeNotification", dependsOn = "activateCard")
    @StepEvent(type = "card.activation.notified")
    public Mono<String> sendWelcomeNotification(ActivateCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
