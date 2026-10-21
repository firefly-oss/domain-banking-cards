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


package io.fireflysoftware.domain.banking.cards.core.limit.workflows;

import io.fireflysoftware.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@Saga(name = "UpdateCardLimitsSaga")
@Service
public class UpdateCardLimitsSaga {

    private final CommandBus commandBus;

    public UpdateCardLimitsSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = "validateLimits")
    @StepEvent(type = "card.limits.validated")
    public Mono<Boolean> validateLimits(UpdateCardLimitsCommand cmd, ExecutionContext ctx) {
        ctx.putVariable("cardId", cmd.getCardId());
        ctx.putVariable("limitId", cmd.getLimitId());
        ctx.putVariable("previousDailyLimit", BigDecimal.ZERO);
        return Mono.just(true);
    }

    @SagaStep(id = "updateLimits", compensate = "revertLimits", dependsOn = "validateLimits")
    @StepEvent(type = "card.limits.updated")
    public Mono<Void> updateLimits(UpdateCardLimitsCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    public Mono<Void> revertLimits(ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable("cardId");
        UUID limitId = (UUID) ctx.getVariable("limitId");
        BigDecimal previousLimit = (BigDecimal) ctx.getVariable("previousDailyLimit");

        return commandBus.<Void>send(UpdateCardLimitsCommand.builder()
                .cardId(cardId)
                .limitId(limitId)
                .dailyLimit(previousLimit)
                .build());
    }

    @SagaStep(id = "notifyLimitChange", dependsOn = "updateLimits")
    @StepEvent(type = "card.limits.change.notified")
    public Mono<String> notifyLimitChange(UpdateCardLimitsCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
