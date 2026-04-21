package com.firefly.domain.banking.cards.core.card.services;

import com.firefly.core.banking.cards.sdk.model.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface CardQueryService {

    Mono<CardDTO> getCard(UUID cardId);

    Mono<CardBalanceDTO> getCardBalance(UUID cardId);

    Flux<CardLimitDTO> getCardLimits(UUID cardId);

    Flux<CardSecurityDTO> getCardSecuritySettings(UUID cardId);

    Mono<CardConfigurationDTO> getCardConfiguration(UUID cardId);

    Flux<CardTransactionDTO> getCardTransactions(UUID cardId, LocalDate from, LocalDate to);

    Mono<PhysicalCardDTO> getPhysicalCard(UUID cardId);

    Flux<VirtualCardDTO> getVirtualCards(UUID cardId);

    Flux<CardDisputeDTO> getCardDisputes(UUID cardId);

    Flux<CardActivityDTO> getCardActivity(UUID cardId);
}
