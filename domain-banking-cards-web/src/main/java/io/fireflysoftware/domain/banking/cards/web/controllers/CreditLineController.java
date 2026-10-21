/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.domain.banking.cards.web.controllers;

import io.fireflysoftware.domain.banking.cards.core.card.services.CardService;
import io.fireflysoftware.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import io.fireflysoftware.domain.banking.cards.interfaces.dtos.CreditLineResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
                    CreditLineResponse body = CreditLineResponse.builder()
                            .revolvingLineId(revolvingLineId)
                            .cardId(cardId)
                            .creditLimit(command.getCreditLimit())
                            .executionId(result.correlationId())
                            .status(result.isSuccess() ? "COMPLETED" : "FAILED")
                            .build();
                    HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
                    return ResponseEntity.status(status).body(body);
                });
    }
}
