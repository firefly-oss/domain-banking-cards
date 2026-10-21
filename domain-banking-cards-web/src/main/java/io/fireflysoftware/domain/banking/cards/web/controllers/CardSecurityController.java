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
import io.fireflysoftware.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
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
@RequestMapping("/api/v1/cards/{cardId}/security")
@RequiredArgsConstructor
@Tag(name = "Card Security", description = "Card security settings management")
public class CardSecurityController {

    private final CardService cardService;

    @PutMapping("/{securitySettingId}")
    @Operation(summary = "Update security settings", description = "Updates security settings for a card")
    public Mono<ResponseEntity<Void>> updateSecuritySettings(
            @PathVariable UUID cardId,
            @PathVariable UUID securitySettingId,
            @Valid @RequestBody UpdateSecuritySettingsCommand command) {
        UpdateSecuritySettingsCommand fullCommand = UpdateSecuritySettingsCommand.builder()
                .cardId(cardId)
                .securitySettingId(securitySettingId)
                .securityFeature(command.getSecurityFeature())
                .enabled(command.isEnabled())
                .build();
        return cardService.updateSecuritySettings(fullCommand)
                .map(result -> {
                    if (result.isSuccess()) {
                        return ResponseEntity.noContent().<Void>build();
                    }
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).<Void>build();
                });
    }
}
