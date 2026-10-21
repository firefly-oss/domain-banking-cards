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

import io.fireflysoftware.domain.banking.cards.core.card.commands.ActivateCardCommand;
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
