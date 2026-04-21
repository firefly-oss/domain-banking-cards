package com.firefly.domain.banking.cards.core.creditline.commands;

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
public class SetupCreditLineCommand implements Command<UUID> {
    private UUID cardId;
    private UUID customerId;
    private BigDecimal creditLimit;
    private BigDecimal interestRate;
    private String currency;
}
