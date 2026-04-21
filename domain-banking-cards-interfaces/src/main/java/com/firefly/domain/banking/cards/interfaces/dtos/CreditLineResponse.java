package com.firefly.domain.banking.cards.interfaces.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditLineResponse {
    private UUID revolvingLineId;
    private UUID cardId;
    private BigDecimal creditLimit;
    private String executionId;
    private String status;
}
