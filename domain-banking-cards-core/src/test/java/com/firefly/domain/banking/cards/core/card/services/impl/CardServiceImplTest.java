package com.firefly.domain.banking.cards.core.card.services.impl;

import com.firefly.domain.banking.cards.core.card.commands.*;
import com.firefly.domain.banking.cards.core.creditline.commands.SetupCreditLineCommand;
import com.firefly.domain.banking.cards.core.limit.commands.UpdateCardLimitsCommand;
import com.firefly.domain.banking.cards.core.security.commands.UpdateSecuritySettingsCommand;
import com.firefly.domain.banking.cards.core.virtual.commands.IssueVirtualCardCommand;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CardServiceImpl Tests")
class CardServiceImplTest {

    @Mock
    private SagaEngine sagaEngine;

    @Mock
    private SagaResult sagaResult;

    private CardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CardServiceImpl(sagaEngine);
    }

    @Test
    @DisplayName("Should issue card successfully")
    void testIssueCard_ShouldExecuteSaga() {
        // Given
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();
        when(sagaEngine.execute(eq("IssueCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.issueCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("IssueCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should activate card successfully")
    void testActivateCard_ShouldExecuteSaga() {
        // Given
        ActivateCardCommand command = ActivateCardCommand.builder()
                .cardId(UUID.randomUUID())
                .activationCode("123456")
                .build();
        when(sagaEngine.execute(eq("ActivateCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.activateCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("ActivateCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should block card successfully")
    void testBlockCard_ShouldExecuteSaga() {
        // Given
        BlockCardCommand command = BlockCardCommand.builder()
                .cardId(UUID.randomUUID())
                .reason("Suspected fraud")
                .blockedBy("CUSTOMER")
                .build();
        when(sagaEngine.execute(eq("BlockCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.blockCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("BlockCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should unblock card successfully")
    void testUnblockCard_ShouldExecuteSaga() {
        // Given
        UnblockCardCommand command = UnblockCardCommand.builder()
                .cardId(UUID.randomUUID())
                .unblockedBy("CUSTOMER")
                .build();
        when(sagaEngine.execute(eq("UnblockCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.unblockCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("UnblockCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should replace card successfully")
    void testReplaceCard_ShouldExecuteSaga() {
        // Given
        ReplaceCardCommand command = ReplaceCardCommand.builder()
                .oldCardId(UUID.randomUUID())
                .replacementReason("Lost")
                .transferLimits(true)
                .transferSecuritySettings(true)
                .build();
        when(sagaEngine.execute(eq("ReplaceCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.replaceCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("ReplaceCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should cancel card successfully")
    void testCancelCard_ShouldExecuteSaga() {
        // Given
        CancelCardCommand command = CancelCardCommand.builder()
                .cardId(UUID.randomUUID())
                .reason("Customer request")
                .cancelledBy("CUSTOMER")
                .build();
        when(sagaEngine.execute(eq("CancelCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.cancelCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("CancelCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should update card limits successfully")
    void testUpdateCardLimits_ShouldExecuteSaga() {
        // Given
        UpdateCardLimitsCommand command = UpdateCardLimitsCommand.builder()
                .cardId(UUID.randomUUID())
                .limitId(UUID.randomUUID())
                .dailyLimit(BigDecimal.valueOf(10000))
                .build();
        when(sagaEngine.execute(eq("UpdateCardLimitsSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.updateCardLimits(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("UpdateCardLimitsSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should update security settings successfully")
    void testUpdateSecuritySettings_ShouldExecuteSaga() {
        // Given
        UpdateSecuritySettingsCommand command = UpdateSecuritySettingsCommand.builder()
                .cardId(UUID.randomUUID())
                .securitySettingId(UUID.randomUUID())
                .enabled(true)
                .build();
        when(sagaEngine.execute(eq("ManageSecuritySettingsSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.updateSecuritySettings(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("ManageSecuritySettingsSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should setup credit line successfully")
    void testSetupCreditLine_ShouldExecuteSaga() {
        // Given
        SetupCreditLineCommand command = SetupCreditLineCommand.builder()
                .cardId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .creditLimit(BigDecimal.valueOf(50000))
                .interestRate(BigDecimal.valueOf(0.18))
                .build();
        when(sagaEngine.execute(eq("SetupCreditLineSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.setupCreditLine(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("SetupCreditLineSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should issue virtual card successfully")
    void testIssueVirtualCard_ShouldExecuteSaga() {
        // Given
        IssueVirtualCardCommand command = IssueVirtualCardCommand.builder()
                .parentCardId(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .spendingLimit(BigDecimal.valueOf(1000))
                .build();
        when(sagaEngine.execute(eq("IssueVirtualCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        // When
        Mono<SagaResult> result = service.issueVirtualCard(command);

        // Then
        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();

        verify(sagaEngine).execute(eq("IssueVirtualCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Should handle saga execution errors")
    void testIssueCard_ShouldHandleErrors() {
        // Given
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();
        RuntimeException error = new RuntimeException("Saga execution failed");
        when(sagaEngine.execute(eq("IssueCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.error(error));

        // When
        Mono<SagaResult> result = service.issueCard(command);

        // Then
        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(sagaEngine).execute(eq("IssueCardSaga"), any(StepInputs.class));
    }

    @Test
    @DisplayName("Constructor should set saga engine dependency")
    void testConstructor_ShouldSetSagaEngine() {
        // When
        CardServiceImpl newService = new CardServiceImpl(sagaEngine);

        // Then
        assertNotNull(newService);
        // Verify it works by calling a method
        IssueCardCommand command = IssueCardCommand.builder()
                .customerId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .cardProgramId(UUID.randomUUID())
                .build();
        when(sagaEngine.execute(eq("IssueCardSaga"), any(StepInputs.class)))
                .thenReturn(Mono.just(sagaResult));

        Mono<SagaResult> result = newService.issueCard(command);

        StepVerifier.create(result)
                .expectNext(sagaResult)
                .verifyComplete();
    }
}
