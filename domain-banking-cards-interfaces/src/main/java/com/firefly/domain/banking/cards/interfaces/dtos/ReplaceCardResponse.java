package com.firefly.domain.banking.cards.interfaces.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceCardResponse {
    private UUID oldCardId;
    private UUID newCardId;
    private String executionId;
    private String status;
}
