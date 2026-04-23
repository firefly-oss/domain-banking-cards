package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command that drives {@code AccrueCardInterestSaga}. Typically invoked by the batch
 * scheduler to post the interest accrual for a credit-card statement period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccrueCardInterestCommand implements Command<UUID> {

    private UUID cardId;
    private BigDecimal interestAmount;
    private BigDecimal interestRate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String calculationMethod;
}
