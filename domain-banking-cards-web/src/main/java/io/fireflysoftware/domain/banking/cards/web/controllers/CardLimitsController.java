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
import io.fireflysoftware.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import io.fireflysoftware.domain.banking.cards.core.limit.handlers.LimitNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.springframework.http.HttpStatus;
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
                .map(CardLimitsController::toVoidResponse);
    }

    /**
     * Maps the saga outcome to an HTTP response. A successful saga returns 204.
     * A failure caused by a missing limit is reported as 404. Any other failure is reported
     * as 422 Unprocessable Entity so callers do not mistake a failed saga for a success.
     */
    private static ResponseEntity<Void> toVoidResponse(SagaResult result) {
        if (result.isSuccess()) {
            return ResponseEntity.noContent().build();
        }
        if (result.error().map(CardLimitsController::isLimitNotFound).orElse(false)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
    }

    private static boolean isLimitNotFound(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof LimitNotFoundException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
