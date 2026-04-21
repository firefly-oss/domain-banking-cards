package com.firefly.domain.banking.cards.core.limit.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupDefaultLimitsCommand implements Command<Void> {
    private UUID cardId;
    private UUID cardProgramId;
}
