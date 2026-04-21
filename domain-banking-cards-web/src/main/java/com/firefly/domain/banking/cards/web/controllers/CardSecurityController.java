package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
                .thenReturn(ResponseEntity.noContent().build());
    }
}
