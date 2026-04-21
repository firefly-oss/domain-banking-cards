package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards/{cardId}/limits")
@RequiredArgsConstructor
@Tag(name = "Card Limits", description = "Card spending and withdrawal limits management")
public class CardLimitsController {

    private final CardService cardService;

    @PutMapping("/{limitId}")
    @Operation(summary = "Update card limits", description = "Updates spending or withdrawal limits for a card")
    public Mono<ResponseEntity<Void>> updateLimits(
            @PathVariable UUID cardId,
            @PathVariable UUID limitId,
            @Valid @RequestBody UpdateCardLimitsCommand command) {
        UpdateCardLimitsCommand fullCommand = UpdateCardLimitsCommand.builder()
                .cardId(cardId)
                .limitId(limitId)
                .dailyLimit(command.getDailyLimit())
                .monthlyLimit(command.getMonthlyLimit())
                .transactionLimit(command.getTransactionLimit())
                .limitType(command.getLimitType())
                .build();
        return cardService.updateCardLimits(fullCommand)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
