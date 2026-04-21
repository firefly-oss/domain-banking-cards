package com.firefly.domain.banking.cards.core.creditline.handlers;

import com.firefly.core.lending.cards.sdk.api.CcRevolvingLineApi;
import com.firefly.core.lending.cards.sdk.model.CcRevolvingLineDTO;
import com.firefly.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
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
