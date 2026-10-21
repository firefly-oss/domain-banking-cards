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


package io.fireflysoftware.domain.banking.cards.core.virtual.workflows;

import io.fireflysoftware.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
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
