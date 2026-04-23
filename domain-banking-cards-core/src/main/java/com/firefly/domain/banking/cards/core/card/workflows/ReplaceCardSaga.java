package com.firefly.domain.banking.cards.core.card.workflows;

import com.firefly.domain.banking.cards.core.card.commands.CancelCardCommand;
import com.firefly.domain.banking.cards.core.card.commands.ReplaceCardCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.firefly.domain.banking.cards.core.card.utils.ReplaceCardConstants.*;

@Saga(name = SAGA_REPLACE_CARD_NAME)
@Service
public class ReplaceCardSaga {

    private final CommandBus commandBus;

    public ReplaceCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = STEP_VALIDATE_OLD_CARD)
    @StepEvent(type = EVENT_OLD_CARD_VALIDATED)
    public Mono<Boolean> validateOldCard(ReplaceCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_OLD_CARD_ID, cmd.getOldCardId());
        return Mono.just(true);
    }

    @SagaStep(id = STEP_CREATE_REPLACEMENT, compensate = COMPENSATE_DELETE_NEW_CARD, dependsOn = STEP_VALIDATE_OLD_CARD)
    @StepEvent(type = EVENT_REPLACEMENT_CREATED)
    public Mono<UUID> createReplacementCard(ReplaceCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<UUID>send(cmd)
                .doOnNext(newCardId -> ctx.putVariable(CTX_NEW_CARD_ID, newCardId));
    }

    public Mono<Void> deleteNewCard(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = STEP_TRANSFER_LIMITS, dependsOn = STEP_CREATE_REPLACEMENT)
    @StepEvent(type = EVENT_LIMITS_TRANSFERRED)
    public Mono<String> transferLimits(ReplaceCardCommand cmd, ExecutionContext ctx) {
        if (!cmd.isTransferLimits()) {
            return Mono.just("skipped");
        }
        return Mono.just("skipped");
    }

    @SagaStep(id = STEP_TRANSFER_SECURITY, dependsOn = STEP_CREATE_REPLACEMENT)
    @StepEvent(type = EVENT_SECURITY_TRANSFERRED)
    public Mono<String> transferSecuritySettings(ReplaceCardCommand cmd, ExecutionContext ctx) {
        if (!cmd.isTransferSecuritySettings()) {
            return Mono.just("skipped");
        }
        return Mono.just("skipped");
    }

    @SagaStep(id = STEP_CANCEL_OLD_CARD, compensate = COMPENSATE_RESTORE_OLD_CARD, dependsOn = {STEP_TRANSFER_LIMITS, STEP_TRANSFER_SECURITY})
    @StepEvent(type = EVENT_OLD_CARD_CANCELLED)
    public Mono<Void> cancelOldCard(ReplaceCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<Void>send(CancelCardCommand.builder()
                .cardId(cmd.getOldCardId())
                .reason(cmd.getReplacementReason())
                .cancelledBy("REPLACEMENT_PROCESS")
                .build());
    }

    public Mono<Void> restoreOldCard(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = STEP_ORDER_PHYSICAL, dependsOn = STEP_CANCEL_OLD_CARD)
    @StepEvent(type = EVENT_PHYSICAL_ORDERED)
    public Mono<String> orderPhysicalReplacement(ReplaceCardCommand cmd, ExecutionContext ctx) {
        return Mono.just("skipped");
    }
}
