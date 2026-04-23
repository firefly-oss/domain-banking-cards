package com.firefly.domain.banking.cards.core.virtual.workflows;

import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "IssueVirtualCardSaga")
@Service
public class IssueVirtualCardSaga {

    private final CommandBus commandBus;

    public IssueVirtualCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateParentCard")
    @StepEvent(type = "virtualcard.parent.validated")
    public Mono<Boolean> validateParentCard(IssueVirtualCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("parentCardId", cmd.getParentCardId());
        ctx.putVariable("customerId", cmd.getCustomerId());
        return Mono.just(true);
    }

    @SagaStep(id = "validateSpendingLimit", dependsOn = "validateParentCard")
    @StepEvent(type = "virtualcard.limit.validated")
    public Mono<Boolean> validateSpendingLimit(IssueVirtualCardCommand cmd, ExecutionContext ctx) {
        return Mono.just(true);
    }

    @SagaStep(id = "createVirtualCard", compensate = "deleteVirtualCard", dependsOn = "validateSpendingLimit")
    @StepEvent(type = "virtualcard.created")
    public Mono<UUID> createVirtualCard(IssueVirtualCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<UUID>send(cmd)
                .doOnNext(virtualCardId -> ctx.putVariable("virtualCardId", virtualCardId));
    }

    public Mono<Void> deleteVirtualCard(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = "setupVirtualCardLimits", dependsOn = "createVirtualCard")
    @StepEvent(type = "virtualcard.limits.setup")
    public Mono<String> setupVirtualCardLimits(IssueVirtualCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }

    @SagaStep(id = "notifyCustomer", dependsOn = "setupVirtualCardLimits")
    @StepEvent(type = "virtualcard.notified")
    public Mono<String> notifyCustomer(IssueVirtualCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
