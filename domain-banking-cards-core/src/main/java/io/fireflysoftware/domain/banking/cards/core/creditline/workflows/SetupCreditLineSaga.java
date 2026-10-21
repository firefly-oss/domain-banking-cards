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


package io.fireflysoftware.domain.banking.cards.core.creditline.workflows;

import io.fireflysoftware.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
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
