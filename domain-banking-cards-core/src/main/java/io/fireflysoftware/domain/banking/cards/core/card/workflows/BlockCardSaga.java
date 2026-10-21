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

import io.fireflysoftware.domain.banking.cards.core.card.commands.BlockCardCommand;
import io.fireflysoftware.domain.banking.cards.core.card.commands.UnblockCardCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static io.fireflysoftware.domain.banking.cards.core.card.utils.BlockCardConstants.*;

@Saga(name = SAGA_BLOCK_CARD_NAME)
@Service
public class BlockCardSaga {

    private final CommandBus commandBus;

    public BlockCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = STEP_VALIDATE_CARD)
    @StepEvent(type = EVENT_CARD_VALIDATED)
    public Mono<Boolean> validateCard(BlockCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CARD_ID, cmd.getCardId());
        ctx.putVariable(CTX_PREVIOUS_STATUS, "ACTIVE");
        return Mono.just(true);
    }

    @SagaStep(id = STEP_BLOCK_CARD, compensate = COMPENSATE_UNBLOCK_CARD, dependsOn = STEP_VALIDATE_CARD)
    @StepEvent(type = EVENT_CARD_BLOCKED)
    public Mono<Void> blockCard(BlockCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(cmd);
    }

    public Mono<Void> unblockCard(ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        return commandBus.<Void>send(UnblockCardCommand.builder()
                .cardId(cardId)
                .unblockedBy("SYSTEM_COMPENSATION")
                .build());
    }

    @SagaStep(id = STEP_NOTIFY_CUSTOMER, dependsOn = STEP_BLOCK_CARD)
    @StepEvent(type = EVENT_CUSTOMER_NOTIFIED)
    public Mono<String> notifyCustomer(BlockCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
