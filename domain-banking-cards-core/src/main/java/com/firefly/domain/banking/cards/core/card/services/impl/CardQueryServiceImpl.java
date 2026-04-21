package com.firefly.domain.banking.cards.core.card.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firefly.core.banking.cards.sdk.model.*;
import com.firefly.domain.banking.cards.core.card.queries.*;
import com.firefly.domain.banking.cards.core.card.services.CardQueryService;
import lombok.RequiredArgsConstructor;
import org.fireflyframework.cqrs.query.QueryBus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardQueryServiceImpl implements CardQueryService {

    private final QueryBus queryBus;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<CardDTO> getCard(UUID cardId) {
        return queryBus.query(GetCardQuery.builder()
                .cardId(cardId)
                .build());
    }

    @Override
    public Mono<CardBalanceDTO> getCardBalance(UUID cardId) {
        return queryBus.query(GetCardBalanceQuery.builder()
                .cardId(cardId)
                .build());
    }

    @Override
    public Flux<CardLimitDTO> getCardLimits(UUID cardId) {
        return queryBus.<PaginationResponse>query(GetCardLimitsQuery.builder()
                        .cardId(cardId)
                        .build())
                .flatMapMany(response -> Flux.fromIterable(response.getContent())
                        .map(item -> objectMapper.convertValue(item, CardLimitDTO.class)));
    }

    @Override
    public Flux<CardSecurityDTO> getCardSecuritySettings(UUID cardId) {
        return queryBus.<PaginationResponse>query(GetCardSecurityQuery.builder()
                        .cardId(cardId)
                        .build())
                .flatMapMany(response -> Flux.fromIterable(response.getContent())
                        .map(item -> objectMapper.convertValue(item, CardSecurityDTO.class)));
    }

    @Override
    public Mono<CardConfigurationDTO> getCardConfiguration(UUID cardId) {
        return queryBus.query(GetCardConfigurationQuery.builder()
                .cardId(cardId)
                .build());
    }

    @Override
    public Flux<CardTransactionDTO> getCardTransactions(UUID cardId, LocalDate from, LocalDate to) {
        return queryBus.<PaginationResponse>query(GetCardTransactionsQuery.builder()
                        .cardId(cardId)
                        .from(from)
                        .to(to)
                        .build())
                .flatMapMany(response -> Flux.fromIterable(response.getContent())
                        .map(item -> objectMapper.convertValue(item, CardTransactionDTO.class)));
    }

    @Override
    public Mono<PhysicalCardDTO> getPhysicalCard(UUID cardId) {
        return queryBus.query(GetPhysicalCardQuery.builder()
                .cardId(cardId)
                .build());
    }

    @Override
    public Flux<VirtualCardDTO> getVirtualCards(UUID cardId) {
        return queryBus.<PaginationResponse>query(GetVirtualCardsQuery.builder()
                        .cardId(cardId)
                        .build())
                .flatMapMany(response -> Flux.fromIterable(response.getContent())
                        .map(item -> objectMapper.convertValue(item, VirtualCardDTO.class)));
    }

    @Override
    public Flux<CardDisputeDTO> getCardDisputes(UUID cardId) {
        return queryBus.<PaginationResponse>query(GetCardDisputesQuery.builder()
                        .cardId(cardId)
                        .build())
                .flatMapMany(response -> Flux.fromIterable(response.getContent())
                        .map(item -> objectMapper.convertValue(item, CardDisputeDTO.class)));
    }

    @Override
    public Flux<CardActivityDTO> getCardActivity(UUID cardId) {
        return queryBus.<PaginationResponse>query(GetCardActivityQuery.builder()
                        .cardId(cardId)
                        .build())
                .flatMapMany(response -> Flux.fromIterable(response.getContent())
                        .map(item -> objectMapper.convertValue(item, CardActivityDTO.class)));
    }
}
