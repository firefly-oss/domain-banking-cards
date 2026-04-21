package com.firefly.domain.banking.cards.core.limit.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCardLimitsCommand implements Command<Void> {
    private UUID cardId;
    private UUID limitId;
    private String limitType;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private BigDecimal transactionLimit;
}
