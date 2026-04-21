package com.firefly.domain.banking.cards.core.card.workflows;

import com.firefly.domain.banking.cards.core.card.commands.IssueCardCommand;
import com.firefly.domain.banking.cards.core.limit.commands.SetupDefaultLimitsCommand;
import com.firefly.domain.banking.cards.core.security.commands.SetupDefaultSecurityCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static com.firefly.domain.banking.cards.core.card.utils.IssueCardConstants.*;

@Saga(name = SAGA_ISSUE_CARD_NAME)
@Service
public class IssueCardSaga {

    private final CommandBus commandBus;

    public IssueCardSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = STEP_VALIDATE_CUSTOMER)
    @StepEvent(type = EVENT_CUSTOMER_VALIDATED)
    public Mono<Boolean> validateCustomer(IssueCardCommand cmd, ExecutionContext ctx) {
        ctx.putVariable(CTX_CUSTOMER_ID, cmd.getCustomerId());
        return Mono.just(true);
    }

    @SagaStep(id = STEP_CREATE_CARD, compensate = COMPENSATE_DELETE_CARD, dependsOn = STEP_VALIDATE_CUSTOMER)
    @StepEvent(type = EVENT_CARD_CREATED)
    public Mono<UUID> createCard(IssueCardCommand cmd, ExecutionContext ctx) {
        return commandBus.<UUID>send(cmd)
                .doOnNext(cardId -> ctx.putVariable(CTX_CARD_ID, cardId));
    }

    public Mono<Void> deleteCard(ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        if (cardId == null) {
            return Mono.empty();
        }
        return Mono.empty();
    }

    @SagaStep(id = STEP_SETUP_LIMITS, compensate = COMPENSATE_REMOVE_LIMITS, dependsOn = STEP_CREATE_CARD)
    @StepEvent(type = EVENT_LIMITS_SET)
    public Mono<Void> setupDefaultLimits(IssueCardCommand cmd, ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        return commandBus.<Void>send(SetupDefaultLimitsCommand.builder()
                .cardId(cardId)
                .cardProgramId(cmd.getCardProgramId())
                .build());
    }

    public Mono<Void> removeLimits(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = STEP_SETUP_SECURITY, compensate = COMPENSATE_REMOVE_SECURITY, dependsOn = STEP_CREATE_CARD)
    @StepEvent(type = EVENT_SECURITY_SET)
    public Mono<Void> setupSecuritySettings(IssueCardCommand cmd, ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        return commandBus.<Void>send(SetupDefaultSecurityCommand.builder()
                .cardId(cardId)
                .build());
    }

    public Mono<Void> removeSecuritySettings(ExecutionContext ctx) {
        return Mono.empty();
    }

    @SagaStep(id = STEP_ORDER_PHYSICAL, compensate = COMPENSATE_CANCEL_ORDER, dependsOn = {STEP_SETUP_LIMITS, STEP_SETUP_SECURITY})
    @StepEvent(type = EVENT_PHYSICAL_ORDERED)
    public Mono<UUID> orderPhysicalCard(IssueCardCommand cmd, ExecutionContext ctx) {
        UUID cardId = (UUID) ctx.getVariable(CTX_CARD_ID);
        ctx.putVariable(CTX_PHYSICAL_CARD_ID, cardId);
        return Mono.just(cardId);
    }

    public Mono<Void> cancelPhysicalCardOrder(ExecutionContext ctx) {
        return Mono.empty();
    }
}
