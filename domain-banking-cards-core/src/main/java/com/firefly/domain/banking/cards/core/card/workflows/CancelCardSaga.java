package com.firefly.domain.banking.cards.core.card.workflows;

import com.firefly.domain.banking.cards.core.card.commands.CancelCardCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "CancelCardSaga")
@Service
public class CancelCardSaga {

    private final CommandBus commandBus;

    public CancelCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateCard")
    @StepEvent(type = "card.cancel.validated")
    public Mono<Boolean> validateCard(CancelCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("cardId", cmd.getCardId());
        return Mono.just(true);
    }

    @SagaStep(id = "settleOutstanding", dependsOn = "validateCard")
    @StepEvent(type = "card.outstanding.settled")
    public Mono<Void> settleOutstanding(CancelCardCommand cmd, ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = "cancelCard", dependsOn = "settleOutstanding")
    @StepEvent(type = "card.cancelled")
    public Mono<Void> cancelCard(CancelCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    @SagaStep(id = "notifyCustomer", dependsOn = "cancelCard")
    @StepEvent(type = "card.cancel.notified")
    public Mono<Void> notifyCustomer(CancelCardCommand cmd, ExecutionContext ctx) {
        return Mono.empty();
    }
}
