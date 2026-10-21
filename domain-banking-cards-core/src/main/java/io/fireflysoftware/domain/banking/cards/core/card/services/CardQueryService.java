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


package io.fireflysoftware.domain.banking.cards.core.card.services;

import io.fireflysoftware.core.banking.cards.sdk.model.*;
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
