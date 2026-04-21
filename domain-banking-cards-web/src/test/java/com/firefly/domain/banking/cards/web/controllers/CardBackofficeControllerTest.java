package com.firefly.domain.banking.cards.web.controllers;

import com.firefly.core.banking.cards.sdk.model.*;
import com.firefly.domain.banking.cards.core.card.services.CardQueryService;
import com.firefly.domain.banking.cards.core.card.services.CardService;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardBackofficeController Tests")
class CardBackofficeControllerTest {

    @Mock
    private CardQueryService cardQueryService;

    @Mock
    private CardService cardService;

    @Mock
    private SagaResult sagaResult;

    private CardBackofficeController controller;

    @BeforeEach
    void setUp() {
        controller = new CardBackofficeController(cardQueryService, cardService);
    }

    @Test
    @DisplayName("Should get card successfully")
    void testGetCard_ShouldReturnCard() {
        UUID cardId = UUID.randomUUID();
        CardDTO card = new CardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        card.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);
        when(cardQueryService.getCard(cardId)).thenReturn(Mono.just(card));

        Mono<ResponseEntity<CardDTO>> result = controller.getCard(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    CardDTO body = response.getBody();
                    assertNotNull(body);
                    assertEquals(cardId, body.getCardId());
                })
                .verifyComplete();

        verify(cardQueryService).getCard(cardId);
    }

    @Test
    @DisplayName("Should get card balance successfully")
    void testGetCardBalance_ShouldReturnBalance() {
        UUID cardId = UUID.randomUUID();
        CardBalanceDTO balance = new CardBalanceDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardBalance(cardId)).thenReturn(Mono.just(balance));

        Mono<ResponseEntity<CardBalanceDTO>> result = controller.getCardBalance(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    assertNotNull(response.getBody());
                })
                .verifyComplete();

        verify(cardQueryService).getCardBalance(cardId);
    }

    @Test
    @DisplayName("Should get card limits successfully")
    void testGetCardLimits_ShouldReturnLimits() {
        UUID cardId = UUID.randomUUID();
        CardLimitDTO limit = new CardLimitDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardLimits(cardId)).thenReturn(Flux.just(limit));

        Mono<ResponseEntity<List<CardLimitDTO>>> result = controller.getCardLimits(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    List<CardLimitDTO> body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.size());
                })
                .verifyComplete();

        verify(cardQueryService).getCardLimits(cardId);
    }

    @Test
    @DisplayName("Should get card security settings successfully")
    void testGetCardSecurity_ShouldReturnSecuritySettings() {
        UUID cardId = UUID.randomUUID();
        CardSecurityDTO security = new CardSecurityDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardSecuritySettings(cardId)).thenReturn(Flux.just(security));

        Mono<ResponseEntity<List<CardSecurityDTO>>> result = controller.getCardSecurity(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    List<CardSecurityDTO> body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.size());
                })
                .verifyComplete();

        verify(cardQueryService).getCardSecuritySettings(cardId);
    }

    @Test
    @DisplayName("Should get card configuration successfully")
    void testGetCardConfiguration_ShouldReturnConfiguration() {
        UUID cardId = UUID.randomUUID();
        CardConfigurationDTO config = new CardConfigurationDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardConfiguration(cardId)).thenReturn(Mono.just(config));

        Mono<ResponseEntity<CardConfigurationDTO>> result = controller.getCardConfiguration(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    assertNotNull(response.getBody());
                })
                .verifyComplete();

        verify(cardQueryService).getCardConfiguration(cardId);
    }

    @Test
    @DisplayName("Should get card transactions successfully")
    void testGetCardTransactions_ShouldReturnTransactions() {
        UUID cardId = UUID.randomUUID();
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();
        CardTransactionDTO transaction = new CardTransactionDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardTransactions(cardId, from, to)).thenReturn(Flux.just(transaction));

        Mono<ResponseEntity<List<CardTransactionDTO>>> result = controller.getCardTransactions(cardId, from, to);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    List<CardTransactionDTO> body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.size());
                })
                .verifyComplete();

        verify(cardQueryService).getCardTransactions(cardId, from, to);
    }

    @Test
    @DisplayName("Should get physical card successfully")
    void testGetPhysicalCard_ShouldReturnPhysicalCard() {
        UUID cardId = UUID.randomUUID();
        PhysicalCardDTO physicalCard = new PhysicalCardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getPhysicalCard(cardId)).thenReturn(Mono.just(physicalCard));

        Mono<ResponseEntity<PhysicalCardDTO>> result = controller.getPhysicalCard(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    assertNotNull(response.getBody());
                })
                .verifyComplete();

        verify(cardQueryService).getPhysicalCard(cardId);
    }

    @Test
    @DisplayName("Should get virtual cards successfully")
    void testGetVirtualCards_ShouldReturnVirtualCards() {
        UUID cardId = UUID.randomUUID();
        VirtualCardDTO virtualCard = new VirtualCardDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getVirtualCards(cardId)).thenReturn(Flux.just(virtualCard));

        Mono<ResponseEntity<List<VirtualCardDTO>>> result = controller.getVirtualCards(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    List<VirtualCardDTO> body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.size());
                })
                .verifyComplete();

        verify(cardQueryService).getVirtualCards(cardId);
    }

    @Test
    @DisplayName("Should get card disputes successfully")
    void testGetCardDisputes_ShouldReturnDisputes() {
        UUID cardId = UUID.randomUUID();
        CardDisputeDTO dispute = new CardDisputeDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardDisputes(cardId)).thenReturn(Flux.just(dispute));

        Mono<ResponseEntity<List<CardDisputeDTO>>> result = controller.getCardDisputes(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    List<CardDisputeDTO> body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.size());
                })
                .verifyComplete();

        verify(cardQueryService).getCardDisputes(cardId);
    }

    @Test
    @DisplayName("Should get card activity successfully")
    void testGetCardActivity_ShouldReturnActivity() {
        UUID cardId = UUID.randomUUID();
        CardActivityDTO activity = new CardActivityDTO(LocalDateTime.now(), LocalDateTime.now(), cardId);
        when(cardQueryService.getCardActivity(cardId)).thenReturn(Flux.just(activity));

        Mono<ResponseEntity<List<CardActivityDTO>>> result = controller.getCardActivity(cardId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    List<CardActivityDTO> body = response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.size());
                })
                .verifyComplete();

        verify(cardQueryService).getCardActivity(cardId);
    }

    @Test
    @DisplayName("Should create virtual card successfully")
    void testCreateVirtualCard_ShouldReturnAcceptedResponse() {
        UUID cardId = UUID.randomUUID();
        UUID virtualCardId = UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();

        CardBackofficeController.CreateVirtualCardRequest request = new CardBackofficeController.CreateVirtualCardRequest();
        request.setCustomerId(UUID.randomUUID());
        request.setSpendingLimit(BigDecimal.valueOf(1000));

        when(sagaResult.resultOf(eq("createVirtualCard"), eq(UUID.class)))
                .thenReturn(Optional.of(virtualCardId));
        when(sagaResult.correlationId()).thenReturn(correlationId);
        when(sagaResult.isSuccess()).thenReturn(true);
        when(cardService.issueVirtualCard(any(IssueVirtualCardCommand.class)))
                .thenReturn(Mono.just(sagaResult));

        Mono<ResponseEntity<Map<String, Object>>> result = controller.createVirtualCard(cardId, request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals(202, response.getStatusCode().value());
                    Map<String, Object> body = response.getBody();
                    assertNotNull(body);
                    assertEquals("COMPLETED", body.get("status"));
                })
                .verifyComplete();

        verify(cardService).issueVirtualCard(any(IssueVirtualCardCommand.class));
    }

    @Test
    @DisplayName("Should handle query errors")
    void testGetCard_ShouldHandleErrors() {
        UUID cardId = UUID.randomUUID();
        RuntimeException error = new RuntimeException("Service error");
        when(cardQueryService.getCard(cardId)).thenReturn(Mono.error(error));

        Mono<ResponseEntity<CardDTO>> result = controller.getCard(cardId);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(cardQueryService).getCard(cardId);
    }

    @Test
    @DisplayName("Constructor should set service dependencies")
    void testConstructor_ShouldSetServices() {
        CardBackofficeController newController = new CardBackofficeController(cardQueryService, cardService);
        assertNotNull(newController);
    }
}
