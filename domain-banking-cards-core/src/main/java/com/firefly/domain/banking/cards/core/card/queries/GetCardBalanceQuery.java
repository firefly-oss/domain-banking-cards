package com.firefly.domain.banking.cards.core.card.queries;

import com.firefly.core.banking.cards.sdk.model.CardBalanceDTO;
import lombok.Builder;
import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

@Data
@Builder
public class GetCardBalanceQuery implements Query<CardBalanceDTO> {
    private UUID cardId;
}
