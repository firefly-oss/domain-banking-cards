package com.firefly.domain.banking.cards.core.card.services.impl;

import com.firefly.domain.banking.cards.core.card.commands.*;
import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import com.firefly.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import com.firefly.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.firefly.domain.banking.cards.core.card.utils.IssueCardConstants.*;

@Service
public class CardServiceImpl implements CardService {

    private final SagaEngine engine;

    public CardServiceImpl(SagaEngine engine) {
        this.engine = engine;
    }

    @Override
    public Mono<SagaResult> issueCard(IssueCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(STEP_VALIDATE_CUSTOMER, command)
                .forStepId(STEP_CREATE_CARD, command)
                .forStepId(STEP_SETUP_LIMITS, command)
                .forStepId(STEP_SETUP_SECURITY, command)
                .forStepId(STEP_ORDER_PHYSICAL, command)
                .build();
        return engine.execute(SAGA_ISSUE_CARD_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> activateCard(ActivateCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateActivationCode", command)
                .forStepId("activateCard", command)
                .forStepId("sendWelcomeNotification", command)
                .build();
        return engine.execute("ActivateCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> blockCard(BlockCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCard", command)
                .forStepId("blockCard", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("BlockCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> unblockCard(UnblockCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCard", command)
                .forStepId("unblockCard", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("UnblockCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> replaceCard(ReplaceCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateOldCard", command)
                .forStepId("createReplacementCard", command)
                .forStepId("transferLimits", command)
                .forStepId("transferSecuritySettings", command)
                .forStepId("cancelOldCard", command)
                .forStepId("orderPhysicalReplacement", command)
                .build();
        return engine.execute("ReplaceCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> cancelCard(CancelCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCard", command)
                .forStepId("settleOutstanding", command)
                .forStepId("cancelCard", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("CancelCardSaga", inputs);
    }

    @Override
    public Mono<SagaResult> updateCardLimits(UpdateCardLimitsCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateLimits", command)
                .forStepId("updateLimits", command)
                .forStepId("notifyLimitChange", command)
                .build();
        return engine.execute("UpdateCardLimitsSaga", inputs);
    }

    @Override
    public Mono<SagaResult> updateSecuritySettings(UpdateSecuritySettingsCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateSecurityChange", command)
                .forStepId("updateSecuritySettings", command)
                .forStepId("notifySecurityChange", command)
                .build();
        return engine.execute("ManageSecuritySettingsSaga", inputs);
    }

    @Override
    public Mono<SagaResult> setupCreditLine(SetupCreditLineCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateCreditRequest", command)
                .forStepId("checkCreditEligibility", command)
                .forStepId("createRevolvingLine", command)
                .forStepId("setupBillingCycle", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("SetupCreditLineSaga", inputs);
    }

    @Override
    public Mono<SagaResult> issueVirtualCard(IssueVirtualCardCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId("validateParentCard", command)
                .forStepId("validateSpendingLimit", command)
                .forStepId("createVirtualCard", command)
                .forStepId("setupVirtualCardLimits", command)
                .forStepId("notifyCustomer", command)
                .build();
        return engine.execute("IssueVirtualCardSaga", inputs);
    }
}
