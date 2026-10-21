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


package io.fireflysoftware.domain.banking.cards.core.card.workflows;

import io.fireflysoftware.domain.banking.cards.core.card.commands.CancelCardCommand;
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
    public Mono<String> settleOutstanding(CancelCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }

    @SagaStep(id = "cancelCard", dependsOn = "settleOutstanding")
    @StepEvent(type = "card.cancelled")
    public Mono<Void> cancelCard(CancelCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    @SagaStep(id = "notifyCustomer", dependsOn = "cancelCard")
    @StepEvent(type = "card.cancel.notified")
    public Mono<String> notifyCustomer(CancelCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
