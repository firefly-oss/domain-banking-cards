package com.firefly.domain.banking.cards.core.creditline.workflows;

import com.firefly.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Saga(name = "SetupCreditLineSaga")
@Service
public class SetupCreditLineSaga {

    private final CommandBus commandBus;

    public SetupCreditLineSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateCreditRequest")
    @StepEvent(type = "creditline.request.validated")
    public Mono<Boolean> validateCreditRequest(SetupCreditLineCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("cardId", cmd.getCardId());
        ctx.putVariable("customerId", cmd.getCustomerId());
        return Mono.just(true);
    }

    @SagaStep(id = "checkCreditEligibility", dependsOn = "validateCreditRequest")
    @StepEvent(type = "creditline.eligibility.checked")
    public Mono<Boolean> checkCreditEligibility(SetupCreditLineCommand cmd, ExecutionContext ctx) {
        return Mono.just(true);
    }

    @SagaStep(id = "createRevolvingLine", compensate = "deleteRevolvingLine", dependsOn = "checkCreditEligibility")
    @StepEvent(type = "creditline.created")
    public Mono<UUID> createRevolvingLine(SetupCreditLineCommand cmd, ExecutionContext ctx) {
        return commandBus.<UUID>send(cmd)
                .doOnNext(revolvingLineId -> ctx.putVariable("revolvingLineId", revolvingLineId));
    }

    public Mono<Void> deleteRevolvingLine(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = "setupBillingCycle", dependsOn = "createRevolvingLine")
    @StepEvent(type = "creditline.billing.setup")
    public Mono<String> setupBillingCycle(SetupCreditLineCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }

    @SagaStep(id = "notifyCustomer", dependsOn = "setupBillingCycle")
    @StepEvent(type = "creditline.notified")
    public Mono<String> notifyCustomer(SetupCreditLineCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
