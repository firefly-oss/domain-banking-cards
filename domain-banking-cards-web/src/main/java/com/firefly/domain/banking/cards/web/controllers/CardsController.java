package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.card.commands.*;
import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.interfaces.dtos.IssueCardResponse;
import com.firefly.domain.banking.cards.interfaces.dtos.ReplaceCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Tag(name = "Cards", description = "Card lifecycle management - issue, activate, block, replace, and cancel cards")
public class CardsController {

    private final CardService cardService;

    @PostMapping
    @Operation(summary = "Issue a new card", description = "Issues a new debit or credit card for a customer")
    public Mono<ResponseEntity<IssueCardResponse>> issueCard(@Valid @RequestBody IssueCardCommand command) {
        return cardService.issueCard(command)
                .map(result -> {
                    UUID cardId = result.resultOf("createCard", UUID.class).orElse(null);
                    return ResponseEntity.ok(IssueCardResponse.builder()
                            .cardId(cardId)
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                            .build());
                });
    }

    @PostMapping("/{cardId}/activate")
    @Operation(summary = "Activate a card", description = "Activates a card using the activation code")
    public Mono<ResponseEntity<Void>> activateCard(
            @PathVariable UUID cardId,
            @RequestParam String activationCode) {
        ActivateCardCommand command = ActivateCardCommand.builder()
                .cardId(cardId)
                .activationCode(activationCode)
                .build();
        return cardService.activateCard(command)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{cardId}/block")
    @Operation(summary = "Block a card", description = "Temporarily blocks a card")
    public Mono<ResponseEntity<Void>> blockCard(
            @PathVariable UUID cardId,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "CUSTOMER") String blockedBy) {
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(cardId)
                .reason(reason)
                .blockedBy(blockedBy)
                .build();
        return cardService.blockCard(command)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{cardId}/unblock")
    @Operation(summary = "Unblock a card", description = "Removes a temporary block from a card")
    public Mono<ResponseEntity<Void>> unblockCard(
            @PathVariable UUID cardId,
            @RequestParam(required = false, defaultValue = "CUSTOMER") String unblockedBy) {
        UnblockCardCommand command = UnblockCardCommand.builder()
                .cardId(cardId)
                .unblockedBy(unblockedBy)
                .build();
        return cardService.unblockCard(command)
                .thenReturn(ResponseEntity.noContent().build());
    }

    @PostMapping("/{cardId}/replace")
    @Operation(summary = "Replace a card", description = "Replaces a lost, stolen, or damaged card")
    public Mono<ResponseEntity<ReplaceCardResponse>> replaceCard(
            @PathVariable UUID cardId,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "true") boolean transferLimits,
            @RequestParam(required = false, defaultValue = "true") boolean transferSecuritySettings) {
        ReplaceCardCommand command = ReplaceCardCommand.builder()
                .oldCardId(cardId)
                .replacementReason(reason)
                .transferLimits(transferLimits)
                .transferSecuritySettings(transferSecuritySettings)
                .build();
        return cardService.replaceCard(command)
                .map(result -> {
                    UUID newCardId = result.resultOf("createReplacementCard", UUID.class).orElse(null);
                    return ResponseEntity.ok(ReplaceCardResponse.builder()
                            .oldCardId(cardId)
                            .newCardId(newCardId)
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                            .build());
                });
    }

    @PostMapping("/{cardId}/cancel")
    @Operation(summary = "Cancel a card", description = "Permanently cancels a card")
    public Mono<ResponseEntity<Void>> cancelCard(
            @PathVariable UUID cardId,
            @RequestParam String reason,
            @RequestParam(required = false, defaultValue = "CUSTOMER") String cancelledBy) {
        CancelCardCommand command = CancelCardCommand.builder()
                .cardId(cardId)
                .reason(reason)
                .cancelledBy(cancelledBy)
                .build();
        return cardService.cancelCard(command)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
