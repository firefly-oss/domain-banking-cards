package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import com.firefly.domain.banking.cards.interfaces.dtos.VirtualCardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards/{cardId}/virtual")
@RequiredArgsConstructor
@Tag(name = "Virtual Cards", description = "Virtual card issuance and management")
public class VirtualCardsController {

    private final CardService cardService;

    @PostMapping
    @Operation(summary = "Issue virtual card", description = "Issues a virtual card linked to a physical card")
    public Mono<ResponseEntity<VirtualCardResponse>> issueVirtualCard(
            @PathVariable UUID cardId,
            @Valid @RequestBody IssueVirtualCardCommand command) {
        IssueVirtualCardCommand fullCommand = IssueVirtualCardCommand.builder()
                .parentCardId(cardId)
                .customerId(command.getCustomerId())
                .purpose(command.getPurpose())
                .spendingLimit(command.getSpendingLimit())
                .expiresAt(command.getExpiresAt())
                .singleUse(command.isSingleUse())
                .build();
        return cardService.issueVirtualCard(fullCommand)
                .map(result -> {
                    UUID virtualCardId = result.resultOf("createVirtualCard", UUID.class).orElse(null);
                    return ResponseEntity.ok(VirtualCardResponse.builder()
                            .virtualCardId(virtualCardId)
                            .parentCardId(cardId)
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                            .build());
                });
    }
}
