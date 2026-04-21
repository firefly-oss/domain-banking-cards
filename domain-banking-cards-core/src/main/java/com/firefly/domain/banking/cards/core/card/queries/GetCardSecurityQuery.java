package com.firefly.domain.banking.cards.core.card.queries;

import com.firefly.core.banking.cards.sdk.model.PaginationResponse;
import lombok.Builder;
import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

@Data
@Builder
public class GetCardSecurityQuery implements Query<PaginationResponse> {
    private UUID cardId;
}
