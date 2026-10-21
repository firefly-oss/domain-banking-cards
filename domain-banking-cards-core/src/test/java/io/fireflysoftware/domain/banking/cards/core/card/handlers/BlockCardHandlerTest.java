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


package io.fireflysoftware.domain.banking.cards.core.card.handlers;

import io.fireflysoftware.core.banking.cards.sdk.api.CardsApi;
import io.fireflysoftware.core.banking.cards.sdk.model.CardDTO;
import io.fireflysoftware.domain.banking.cards.core.card.commands.BlockCardCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockCardHandler Tests")
class BlockCardHandlerTest {

    @Mock
    private CardsApi cardsApi;

    @Captor
    private ArgumentCaptor<CardDTO> cardDTOCaptor;

    private BlockCardHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BlockCardHandler(cardsApi);
    }

    @Test
    @DisplayName("Should block card successfully")
    void testDoHandle_ShouldBlockCard() {
        // Given
        UUID cardId = UUID.randomUUID();
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(cardId)
                .reason("Suspected fraud")
                .blockedBy("CUSTOMER")
                .build();

        CardDTO existingCard = new CardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        existingCard.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);

        CardDTO updatedCard = new CardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        updatedCard.setCardStatus(CardDTO.CardStatusEnum.BLOCKED);

        when(cardsApi.getCard(eq(cardId), any(String.class)))
                .thenReturn(Mono.just(existingCard));
        when(cardsApi.updateCard(eq(cardId), any(CardDTO.class), any(String.class)))
                .thenReturn(Mono.just(updatedCard));

        // When
        Mono<Void> result = handler.handle(command);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(cardsApi).getCard(eq(cardId), any(String.class));
        verify(cardsApi).updateCard(eq(cardId), cardDTOCaptor.capture(), any(String.class));
        assertEquals(CardDTO.CardStatusEnum.BLOCKED, cardDTOCaptor.getValue().getCardStatus());
    }

    @Test
    @DisplayName("Should handle card not found error")
    void testDoHandle_ShouldHandleCardNotFoundError() {
        // Given
        UUID cardId = UUID.randomUUID();
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(cardId)
                .reason("Suspected fraud")
                .blockedBy("CUSTOMER")
                .build();

        RuntimeException error = new RuntimeException("Card not found");
        when(cardsApi.getCard(eq(cardId), any(String.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<Void> result = handler.handle(command);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(cardsApi).getCard(eq(cardId), any(String.class));
        verify(cardsApi, never()).updateCard(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle update error")
    void testDoHandle_ShouldHandleUpdateError() {
        // Given
        UUID cardId = UUID.randomUUID();
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(cardId)
                .reason("Suspected fraud")
                .blockedBy("CUSTOMER")
                .build();

        CardDTO existingCard = new CardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        existingCard.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);

        RuntimeException error = new RuntimeException("Update failed");

        when(cardsApi.getCard(eq(cardId), any(String.class)))
                .thenReturn(Mono.just(existingCard));
        when(cardsApi.updateCard(eq(cardId), any(CardDTO.class), any(String.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<Void> result = handler.handle(command);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(cardsApi).getCard(eq(cardId), any(String.class));
        verify(cardsApi).updateCard(eq(cardId), any(CardDTO.class), any(String.class));
    }

    @Test
    @DisplayName("Constructor should set CardsApi dependency")
    void testConstructor_ShouldSetCardsApi() {
        // When
        BlockCardHandler newHandler = new BlockCardHandler(cardsApi);

        // Then
        assertNotNull(newHandler);
    }
}
