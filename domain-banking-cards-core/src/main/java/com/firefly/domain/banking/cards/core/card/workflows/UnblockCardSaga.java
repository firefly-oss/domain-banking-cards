package com.firefly.domain.banking.cards.core.card.workflows;

import com.firefly.domain.banking.cards.core.card.commands.BlockCardCommand;
import com.firefly.domain.banking.cards.core.card.commands.UnblockCardCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "UnblockCardSaga")
@Service
public class UnblockCardSaga {

    private final CommandBus commandBus;

    public UnblockCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateCard")
    @StepEvent(type = "card.validated")
    public Mono<Boolean> validateCard(UnblockCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("cardId", cmd.getCardId());
        return Mono.just(true);
    }

    @SagaStep(id = "unblockCard", compensate = "reblockCard", dependsOn = "validateCard")
    @StepEvent(type = "card.unblocked")
    public Mono<Void> unblockCard(UnblockCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    public Mono<Void> reblockCard(ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable("cardId");
        return commandBus.<Void>send(BlockCardCommand.builder()
                .cardId(cardId)
                .reason("SYSTEM_COMPENSATION")
                .blockedBy("SYSTEM")
                .build());
    }

    @SagaStep(id = "notifyCustomer", dependsOn = "unblockCard")
    @StepEvent(type = "card.unblock.notified")
    public Mono<Void> notifyCustomer(UnblockCardCommand cmd, ExecutionContext ctx) {
        return Mono.empty();
    }
}
