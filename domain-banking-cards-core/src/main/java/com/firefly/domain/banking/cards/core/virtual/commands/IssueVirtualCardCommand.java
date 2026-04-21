package com.firefly.domain.banking.cards.core.virtual.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueVirtualCardCommand implements Command<UUID> {
    private UUID parentCardId;
    private UUID customerId;
    private String purpose;
    private BigDecimal spendingLimit;
    private LocalDateTime expiresAt;
    private boolean singleUse;
}
