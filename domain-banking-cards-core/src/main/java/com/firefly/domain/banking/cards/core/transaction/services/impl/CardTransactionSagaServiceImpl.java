package com.firefly.domain.banking.cards.core.transaction.services.impl;

import com.firefly.domain.banking.cards.core.transaction.commands.AccrueCardInterestCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.AuthorizeCardTransactionCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ChargeCardFeeCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ClearCardTransactionCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.PostCardStatementPaymentCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ResolveCardDisputeCommand;
import com.firefly.domain.banking.cards.core.transaction.commands.ReverseCardAuthorizationCommand;
import com.firefly.domain.banking.cards.core.transaction.services.CardTransactionSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.SAGA_AUTHORIZE_CARD_TX_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_CREATE_CARD_TX;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_PLACE_LEDGER_HOLD;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_RESOLVE_ACCOUNT;
import static com.firefly.domain.banking.cards.core.transaction.utils.AuthorizeCardTransactionConstants.STEP_UPDATE_CARD_BALANCE_PROJECTION;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.SAGA_CLEAR_CARD_TX_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_LOOKUP_AUTH;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_POST_CLEARING_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_REFRESH_CARD_BALANCE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ClearCardTransactionConstants.STEP_UPDATE_CARD_TX;
import static com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.SAGA_ACCRUE_INTEREST_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.SAGA_CHARGE_FEE_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.SAGA_POST_STATEMENT_PAYMENT_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_CREATE_CARD_PAYMENT;
import static com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_POST_PAYMENT_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.SAGA_RESOLVE_DISPUTE_NAME;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_LOAD_DISPUTE;
import static com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_POST_CHARGEBACK_LEDGER;
import static com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.SAGA_REVERSE_CARD_AUTH_NAME;

/**
 * Thin service that binds each card-transaction-flow saga's command to the
 * {@link SagaEngine} entry point. Keeps the controllers free of orchestration details and
 * leaves the saga definitions as the single source of truth for step wiring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CardTransactionSagaServiceImpl implements CardTransactionSagaService {

    private final SagaEngine engine;

    @Override
    public Mono<SagaResult> authorizeCardTransaction(AuthorizeCardTransactionCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(STEP_RESOLVE_ACCOUNT, command)
                .forStepId(STEP_CREATE_CARD_TX, command)
                .forStepId(STEP_PLACE_LEDGER_HOLD, command)
                .forStepId(STEP_UPDATE_CARD_BALANCE_PROJECTION, command)
                .build();
        return engine.execute(SAGA_AUTHORIZE_CARD_TX_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> clearCardTransaction(ClearCardTransactionCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(STEP_LOOKUP_AUTH, command)
                .forStepId(STEP_POST_CLEARING_LEDGER, command)
                .forStepId(STEP_UPDATE_CARD_TX, command)
                .forStepId(STEP_REFRESH_CARD_BALANCE, command)
                .build();
        return engine.execute(SAGA_CLEAR_CARD_TX_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> reverseCardAuthorization(ReverseCardAuthorizationCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_LOOKUP_AUTH,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_POST_REVERSAL_LEDGER,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_UPDATE_CARD_TX,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ReverseCardAuthorizationConstants.STEP_REFRESH_CARD_BALANCE,
                        command)
                .build();
        return engine.execute(SAGA_REVERSE_CARD_AUTH_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> resolveCardDispute(ResolveCardDisputeCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(STEP_LOAD_DISPUTE, command)
                .forStepId(STEP_POST_CHARGEBACK_LEDGER, command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_UPDATE_DISPUTE,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ResolveCardDisputeConstants.STEP_REFRESH_CARD_BALANCE,
                        command)
                .build();
        return engine.execute(SAGA_RESOLVE_DISPUTE_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> postCardStatementPayment(PostCardStatementPaymentCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(STEP_CREATE_CARD_PAYMENT, command)
                .forStepId(STEP_POST_PAYMENT_LEDGER, command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_UPDATE_CARD_PAYMENT,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_UPDATE_STATEMENT,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.PostCardStatementPaymentConstants.STEP_REFRESH_CARD_BALANCE,
                        command)
                .build();
        return engine.execute(SAGA_POST_STATEMENT_PAYMENT_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> accrueCardInterest(AccrueCardInterestCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_RESOLVE_CARD,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_POST_INTEREST_LEDGER,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.AccrueCardInterestConstants.STEP_UPDATE_STATEMENT,
                        command)
                .build();
        return engine.execute(SAGA_ACCRUE_INTEREST_NAME, inputs);
    }

    @Override
    public Mono<SagaResult> chargeCardFee(ChargeCardFeeCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_RESOLVE_CARD,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_POST_FEE_LEDGER,
                        command)
                .forStepId(
                        com.firefly.domain.banking.cards.core.transaction.utils.ChargeCardFeeConstants.STEP_UPDATE_STATEMENT,
                        command)
                .build();
        return engine.execute(SAGA_CHARGE_FEE_NAME, inputs);
    }
}
