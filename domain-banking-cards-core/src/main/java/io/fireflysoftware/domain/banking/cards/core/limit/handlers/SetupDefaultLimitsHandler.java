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
