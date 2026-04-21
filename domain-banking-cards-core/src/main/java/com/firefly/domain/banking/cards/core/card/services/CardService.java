package com.firefly.domain.banking.cards.core.card.services;

import com.firefly.domain.banking.cards.core.card.commands.*;
import com.firefly.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import com.firefly.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import com.firefly.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import reactor.core.publisher.Mono;

public interface CardService {

    Mono<SagaResult> issueCard(IssueCardCommand command);

    Mono<SagaResult> activateCard(ActivateCardCommand command);

    Mono<SagaResult> blockCard(BlockCardCommand command);

    Mono<SagaResult> unblockCard(UnblockCardCommand command);

    Mono<SagaResult> replaceCard(ReplaceCardCommand command);

    Mono<SagaResult> cancelCard(CancelCardCommand command);

    Mono<SagaResult> updateCardLimits(UpdateCardLimitsCommand command);

    Mono<SagaResult> updateSecuritySettings(UpdateSecuritySettingsCommand command);

    Mono<SagaResult> setupCreditLine(SetupCreditLineCommand command);

    Mono<SagaResult> issueVirtualCard(IssueVirtualCardCommand command);
}
