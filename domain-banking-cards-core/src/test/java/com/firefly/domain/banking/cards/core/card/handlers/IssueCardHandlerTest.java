package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.domain.banking.cards.core.card.commands.IssueCardCommand;
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
@DisplayName("IssueCardHandler Tests")
class IssueCardHandlerTest {

    @Mock
    private CardsApi cardsApi;

    @Captor
    private ArgumentCaptor<CardDTO> cardDTOCaptor;

    private IssueCardHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IssueCardHandler(cardsApi);
    }

    @Test
    @DisplayName("Should issue card successfully")
    void testDoHandle_ShouldCreateCardAndReturnCardId() {
        // Given
        UUID customerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID cardProgramId = UUID.randomUUID();
        UUID expectedCardId = UUID.randomUUID();

        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(customerId)
                .accountId(accountId)
                .cardProgramId(cardProgramId)
                .build();

        CardDTO createdCard = new CardDTO(LocalDateTime.now(), LocalDateTime.now(), expectedCardId);
        createdCard.setPartyId(customerId);
        createdCard.setAccountId(accountId);

        when(cardsApi.createCard(any(CardDTO.class), any(String.class)))
                .thenReturn(Mono.just(createdCard));

        // When
        Mono<UUID> result = handler.handle(command);

        // Then
        StepVerifier.create(result)
                .expectNext(expectedCardId)
                .verifyComplete();

        verify(cardsApi).createCard(cardDTOCaptor.capture(), any(String.class));
        CardDTO capturedCard = cardDTOCaptor.getValue();
        assertEquals(customerId, capturedCard.getPartyId());
        assertEquals(accountId, capturedCard.getAccountId());
        assertEquals(cardProgramId, capturedCard.getCardTypeId());
        assertEquals(CardDTO.CardStatusEnum.ACTIVE, capturedCard.getCardStatus());
    }

    @Test
    @DisplayName("Should handle API errors")
    void testDoHandle_ShouldHandleErrors() {
        // Given
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();

        RuntimeException error = new RuntimeException("API error");
        when(cardsApi.createCard(any(CardDTO.class), any(String.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<UUID> result = handler.handle(command);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(cardsApi).createCard(any(CardDTO.class), any(String.class));
    }

    @Test
    @DisplayName("Should set card status to ACTIVE")
    void testDoHandle_ShouldSetCardStatusToActive() {
        // Given
        UUID cardId = UUID.randomUUID();
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();

        CardDTO createdCard = new CardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);

        when(cardsApi.createCard(cardDTOCaptor.capture(), any(String.class)))
                .thenReturn(Mono.just(createdCard));

        // When
        handler.handle(command).block();

        // Then
        CardDTO capturedCard = cardDTOCaptor.getValue();
        assertEquals(CardDTO.CardStatusEnum.ACTIVE, capturedCard.getCardStatus());
    }

    @Test
    @DisplayName("Constructor should set CardsApi dependency")
    void testConstructor_ShouldSetCardsApi() {
        // When
        IssueCardHandler newHandler = new IssueCardHandler(cardsApi);

        // Then
        assertNotNull(newHandler);
    }
}
