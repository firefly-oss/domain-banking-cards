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

import static com.firefly.domain.banking.cards.core.card.utils.BlockCardConstants.*;

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
