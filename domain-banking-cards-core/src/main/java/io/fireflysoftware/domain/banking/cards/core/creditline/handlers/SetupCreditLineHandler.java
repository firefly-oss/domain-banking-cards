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


package io.fireflysoftware.domain.banking.cards.core.creditline.handlers;

import io.fireflysoftware.core.lending.cards.sdk.api.CcRevolvingLineApi;
import io.fireflysoftware.core.lending.cards.sdk.model.CcRevolvingLineDTO;
import io.fireflysoftware.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.util.UUID;

@CommandHandlerComponent
public class SetupCreditLineHandler extends CommandHandler<SetupCreditLineCommand, UUID> {

    private final CcRevolvingLineApi ccRevolvingLineApi;

    public SetupCreditLineHandler(CcRevolvingLineApi ccRevolvingLineApi) {
        this.ccRevolvingLineApi = ccRevolvingLineApi;
    }

    @Override
    protected Mono<UUID> doHandle(SetupCreditLineCommand cmd) {
        String correlationId = UUID.randomUUID().toString();

        CcRevolvingLineDTO revolvingLine = new CcRevolvingLineDTO();
        revolvingLine.setCardId(cmd.getCardId());
        revolvingLine.setCreditLimit(cmd.getCreditLimit());
        revolvingLine.setInterestRate(cmd.getInterestRate());

        return ccRevolvingLineApi.create(revolvingLine, correlationId)
                .map(CcRevolvingLineDTO::getCcRevolvingLineId);
    }
}
