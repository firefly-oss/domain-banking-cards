package com.firefly.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command that drives {@code ChargeCardFeeSaga}. Posts a fee — annual, late-payment, cash
 * advance, or foreign transaction — as a DEBIT against the cardholder and a CREDIT to the
 * fee-income GL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeCardFeeCommand implements Command<UUID> {

    private UUID cardId;
    private String feeType;
    private BigDecimal feeAmount;
    private String feeCalculationMethod;
    private Boolean waived;
    private String waiverReason;
}
