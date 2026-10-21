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


package io.fireflysoftware.domain.banking.cards.core.limit.handlers;

import io.fireflysoftware.core.banking.cards.sdk.api.CardLimitsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.CardLimitDTO;
import io.fireflysoftware.domain.banking.cards.core.limit.commands.SetupDefaultLimitsCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

@CommandHandlerComponent
public class SetupDefaultLimitsHandler extends CommandHandler<SetupDefaultLimitsCommand, Void> {

    private final CardLimitsApi cardLimitsApi;

    public SetupDefaultLimitsHandler(CardLimitsApi cardLimitsApi) {
        this.cardLimitsApi = cardLimitsApi;
    }

    @Override
    protected Mono<Void> doHandle(SetupDefaultLimitsCommand cmd) {
        String correlationId = UUID.randomUUID().toString();

        CardLimitDTO dailyLimit = new CardLimitDTO();
        dailyLimit.setCardId(cmd.getCardId());
        dailyLimit.setLimitType(CardLimitDTO.LimitTypeEnum.DAILY_SPENDING);
        dailyLimit.setLimitAmount(BigDecimal.valueOf(5000));
        dailyLimit.setResetPeriod(CardLimitDTO.ResetPeriodEnum.DAILY);

        return cardLimitsApi.createLimit(cmd.getCardId(), dailyLimit, correlationId)
                .then();
    }
}
