package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.domain.banking.cards.core.card.commands.*;
import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.interfaces.dtos.IssueCardResponse;
import com.firefly.domain.banking.cards.interfaces.dtos.ReplaceCardResponse;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardsController Tests")
class CardsControllerTest {

    @Mock
    private CardService cardService;

    @Mock
    private SagaResult sagaResult;

    private CardsController controller;

    @BeforeEach
    void setUp() {
        controller = new CardsController(cardService);
    }

    @Test
    @DisplayName("Should issue card successfully")
    void testIssueCard_ShouldReturnOkResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();

        when(sagaResult.resultOf(eq("createCard"), eq(UUID.class)))
                .thenReturn(Optional.of(cardId));
        when(sagaResult.correlationId()).thenReturn(correlationId);
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.issueCard(any(IssueCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<IssueCardResponse>> result = controller.issueCard(command);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    IssueCardResponse body = response.getBody();
                    assertNotNull(body);
                    assertEquals(cardId, body.getCardId());
                    assertEquals("COMPLETED", body.getStatus());
                })
                .verifyComplete();

        verify(cardService).issueCard(any(IssueCardCommand.class));
    }

    @Test
    @DisplayName("Should handle issue card errors")
    void testIssueCard_ShouldHandleErrors() {
        // Given
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();
        RuntimeException error = new RuntimeException("Service error");
        when(cardService.issueCard(any(IssueCardCommand.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<ResponseEntity<IssueCardResponse>> result = controller.issueCard(command);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(cardService).issueCard(any(IssueCardCommand.class));
    }

    @Test
    @DisplayName("Should activate card successfully")
    void testActivateCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String activationCode = "123456";
        when(cardService.activateCard(any(ActivateCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.activateCard(cardId, activationCode);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).activateCard(any(ActivateCardCommand.class));
    }

    @Test
    @DisplayName("Should block card successfully")
    void testBlockCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String reason = "Suspected fraud";
        String blockedBy = "CUSTOMER";
        when(cardService.blockCard(any(BlockCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.blockCard(cardId, reason, blockedBy);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).blockCard(any(BlockCardCommand.class));
    }

    @Test
    @DisplayName("Should unblock card successfully")
    void testUnblockCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String unblockedBy = "CUSTOMER";
        when(cardService.unblockCard(any(UnblockCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.unblockCard(cardId, unblockedBy);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).unblockCard(any(UnblockCardCommand.class));
    }

    @Test
    @DisplayName("Should replace card successfully")
    void testReplaceCard_ShouldReturnOkResponse() {
        // Given
        UUID oldCardId = UUID.randomUUID();
        UUID newCardId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        String reason = "Lost";

        when(sagaResult.resultOf(eq("createReplacementCard"), eq(UUID.class)))
                .thenReturn(Optional.of(newCardId));
        when(sagaResult.correlationId()).thenReturn(correlationId);
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.replaceCard(any(ReplaceCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<ReplaceCardResponse>> result = controller.replaceCard(oldCardId, reason, true, true);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    ReplaceCardResponse body = response.getBody();
                    assertNotNull(body);
                    assertEquals(oldCardId, body.getOldCardId());
                    assertEquals(newCardId, body.getNewCardId());
                    assertEquals("COMPLETED", body.getStatus());
                })
                .verifyComplete();

        verify(cardService).replaceCard(any(ReplaceCardCommand.class));
    }

    @Test
    @DisplayName("Should cancel card successfully")
    void testCancelCard_ShouldReturnNoContentResponse() {
        // Given
        UUID cardId = UUID.randomUUID();
        String reason = "Customer request";
        String cancelledBy = "CUSTOMER";
        when(cardService.cancelCard(any(CancelCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<ResponseEntity<Void>> result = controller.cancelCard(cardId, reason, cancelledBy);

        // Then
        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();

        verify(cardService).cancelCard(any(CancelCardCommand.class));
    }

    @Test
    @DisplayName("Constructor should set service dependency")
    void testConstructor_ShouldSetService() {
        // When
        CardsController newController = new CardsController(cardService);

        // Then
        assertNotNull(newController);

        // Verify it works by calling a method
        UUID cardId = UUID.randomUUID();
        when(cardService.blockCard(any(BlockCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        Mono<ResponseEntity<Void>> result = newController.blockCard(cardId, "test", "CUSTOMER");

        StepVerifier.create(result)
                .assertNext(response -> assertEquals(204, response.getStatusCode().value()))
                .verifyComplete();
    }
}
