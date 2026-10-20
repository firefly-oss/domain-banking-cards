package io.fireflysoftware.domain.banking.cards.core.card.queries;

import io.fireflysoftware.core.banking.cards.sdk.model.CardDTO;
import lombok.Builder;
import lombok.Data;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

@Data
@Builder
public class GetCardQuery implements Query<CardDTO> {
    private UUID cardId;
}
