package io.fireflysoftware.domain.banking.cards.core.card.queries;

import io.fireflysoftware.core.banking.cards.sdk.model.PaginationResponse;
import lombok.Builder;
import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

@Data
@Builder
public class GetCardActivityQuery implements Query<PaginationResponse> {
    private UUID cardId;
}
