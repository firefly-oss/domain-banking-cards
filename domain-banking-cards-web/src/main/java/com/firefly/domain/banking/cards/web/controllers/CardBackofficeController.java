package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.core.banking.cards.sdk.model.*;
import com.firefly.domain.banking.cards.core.card.services.CardQueryService;
import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backoffice/cards")
@RequiredArgsConstructor
@Tag(name = "Card Backoffice", description = "Administrative operations for card management")
public class CardBackofficeController {

    private final CardQueryService cardQueryService;
    private final CardService cardService;

    @GetMapping("/{cardId}")
    @Operation(summary = "Get card details", description = "Retrieves full card information for backoffice review")
    public Mono<ResponseEntity<CardDTO>> getCard(@PathVariable UUID cardId) {
        return cardQueryService.getCard(cardId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{cardId}/balance")
    @Operation(summary = "Get card balance", description = "Retrieves the current balance of a card")
    public Mono<ResponseEntity<CardBalanceDTO>> getCardBalance(@PathVariable UUID cardId) {
        return cardQueryService.getCardBalance(cardId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{cardId}/limits")
    @Operation(summary = "Get card limits", description = "Retrieves all spending limits configured for the card")
    public Mono<ResponseEntity<List<CardLimitDTO>>> getCardLimits(@PathVariable UUID cardId) {
        return cardQueryService.getCardLimits(cardId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{cardId}/security")
    @Operation(summary = "Get card security settings", description = "Retrieves security configurations for the card")
    public Mono<ResponseEntity<List<CardSecurityDTO>>> getCardSecurity(@PathVariable UUID cardId) {
        return cardQueryService.getCardSecuritySettings(cardId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{cardId}/configuration")
    @Operation(summary = "Get card configuration", description = "Retrieves the configuration settings for the card")
    public Mono<ResponseEntity<CardConfigurationDTO>> getCardConfiguration(@PathVariable UUID cardId) {
        return cardQueryService.getCardConfiguration(cardId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{cardId}/transactions")
    @Operation(summary = "Get card transactions", description = "Retrieves transactions for the card within a date range")
    public Mono<ResponseEntity<List<CardTransactionDTO>>> getCardTransactions(
            @PathVariable UUID cardId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return cardQueryService.getCardTransactions(cardId, from, to)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{cardId}/physical")
    @Operation(summary = "Get physical card details", description = "Retrieves physical card information")
    public Mono<ResponseEntity<PhysicalCardDTO>> getPhysicalCard(@PathVariable UUID cardId) {
        return cardQueryService.getPhysicalCard(cardId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/{cardId}/virtual")
    @Operation(summary = "Get virtual cards", description = "Retrieves all virtual cards linked to the parent card")
    public Mono<ResponseEntity<List<VirtualCardDTO>>> getVirtualCards(@PathVariable UUID cardId) {
        return cardQueryService.getVirtualCards(cardId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{cardId}/disputes")
    @Operation(summary = "Get card disputes", description = "Retrieves all disputes associated with the card")
    public Mono<ResponseEntity<List<CardDisputeDTO>>> getCardDisputes(@PathVariable UUID cardId) {
        return cardQueryService.getCardDisputes(cardId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{cardId}/activity")
    @Operation(summary = "Get card activity", description = "Retrieves activity history for the card")
    public Mono<ResponseEntity<List<CardActivityDTO>>> getCardActivity(@PathVariable UUID cardId) {
        return cardQueryService.getCardActivity(cardId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{cardId}/virtual")
    @Operation(summary = "Create virtual card", description = "Creates a virtual card (saga operation)")
    public Mono<ResponseEntity<Map<String, Object>>> createVirtualCard(
            @PathVariable UUID cardId,
            @RequestBody CreateVirtualCardRequest request) {
        IssueVirtualCardCommand command = IssueVirtualCardCommand.builder()
                .parentCardId(cardId)
                .customerId(request.getCustomerId())
                .spendingLimit(request.getSpendingLimit())
                .build();
        return cardService.issueVirtualCard(command)
                .map(result -> {
                    UUID virtualCardId = result.resultOf("createVirtualCard", UUID.class).orElse(null);
                    return ResponseEntity.accepted().body(Map.<String, Object>of(
                            "parentCardId", cardId,
                            "virtualCardId", virtualCardId != null ? virtualCardId.toString() : "pending",
                            "executionId", result.correlationId(),
                            "status", result.isSuccess() ? "COMPLETED" : "FAILED"
                    ));
                });
    }

    @PutMapping("/{cardId}/security")
    @Operation(summary = "Update security", description = "Updates card security settings")
    public Mono<ResponseEntity<Void>> updateSecurity(
            @PathVariable UUID cardId,
            @RequestBody UpdateSecuritySettingsRequest request) {
        UpdateSecuritySettingsCommand command = UpdateSecuritySettingsCommand.builder()
                .cardId(cardId)
                .securitySettingId(request.getSecuritySettingId())
                .enabled(request.isEnabled())
                .build();
        return cardService.updateSecuritySettings(command)
                .thenReturn(ResponseEntity.ok().build());
    }

    @lombok.Data
    public static class UpdateSecuritySettingsRequest {
        private UUID securitySettingId;
        private boolean enabled;
    }

    @lombok.Data
    public static class CreateVirtualCardRequest {
        private UUID customerId;
        private BigDecimal spendingLimit;
    }
}
