package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import com.firefly.domain.banking.cards.interfaces.dtos.CreditLineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards/{cardId}/credit-line")
@RequiredArgsConstructor
@Tag(name = "Credit Line", description = "Credit card revolving line management")
public class CreditLineController {

    private final CardService cardService;

    @PostMapping
    @Operation(summary = "Setup credit line", description = "Sets up a revolving credit line for a credit card")
    public Mono<ResponseEntity<CreditLineResponse>> setupCreditLine(
            @PathVariable UUID cardId,
            @Valid @RequestBody SetupCreditLineCommand command) {
        SetupCreditLineCommand fullCommand = SetupCreditLineCommand.builder()
                .cardId(cardId)
                .customerId(command.getCustomerId())
                .creditLimit(command.getCreditLimit())
                .interestRate(command.getInterestRate())
                .currency(command.getCurrency())
                .build();
        return cardService.setupCreditLine(fullCommand)
                .map(result -> {
                    UUID revolvingLineId = result.resultOf("createRevolvingLine", UUID.class).orElse(null);
                    return ResponseEntity.ok(CreditLineResponse.builder()
                            .revolvingLineId(revolvingLineId)
                            .cardId(cardId)
                            .creditLimit(command.getCreditLimit())
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                            .build());
                });
    }
}
