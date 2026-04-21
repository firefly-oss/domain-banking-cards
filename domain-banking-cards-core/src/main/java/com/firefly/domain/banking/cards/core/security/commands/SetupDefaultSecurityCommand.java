package com.firefly.domain.banking.cards.core.security.commands;

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
public class SetupDefaultSecurityCommand implements Command<Void> {
    private UUID cardId;
}
