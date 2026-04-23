package com.firefly.domain.banking.cards.core.ledger.handlers;

import com.firefly.core.banking.ledger.sdk.api.TransactionLegsApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineCardApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineFeeApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineInterestApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionLineTransferApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionStatusHistoryApi;
import com.firefly.core.banking.ledger.sdk.api.TransactionsApi;
import com.firefly.core.banking.ledger.sdk.model.TransactionDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLegDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineCardDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineFeeDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineInterestDTO;
import com.firefly.core.banking.ledger.sdk.model.TransactionLineTransferDTO;
import com.firefly.domain.banking.cards.core.ledger.commands.LedgerLegSpec;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionCommand;
import com.firefly.domain.banking.cards.core.ledger.commands.PostLedgerTransactionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reusable handler that posts a double-entry ledger transaction through core-banking-ledger for
 * every cards-domain saga step that mutates money. The handler:
 *
 * <ol>
 *   <li>validates that Σ DEBIT equals Σ CREDIT across the provided legs;</li>
 *   <li>creates the transaction header in PENDING status;</li>
 *   <li>posts every leg;</li>
 *   <li>posts the optional typed line (card / fee / interest / transfer);</li>
 *   <li>transitions the transaction to the caller's initialStatus — defaulting to POSTED,
 *       or leaving it PENDING for card authorizations and other holds.</li>
 * </ol>
 *
 * Every mutating SDK call passes a fresh {@code UUID.randomUUID().toString()} as xIdempotencyKey.
 * The command's {@code externalReference} carries the end-to-end idempotency identity (typically
 * a network authorization/clearing reference or {@code sagaId + ":" + stepId}), which the ledger
 * enforces as unique.
 */
@Slf4j
@RequiredArgsConstructor
@CommandHandlerComponent
public class PostLedgerTransactionHandler
        extends CommandHandler<PostLedgerTransactionCommand, PostLedgerTransactionResult> {

    static final String LEG_TYPE_DEBIT = "DEBIT";
    static final String LEG_TYPE_CREDIT = "CREDIT";

    static final String LINE_TYPE_CARD = "CARD";
    static final String LINE_TYPE_FEE = "FEE";
    static final String LINE_TYPE_INTEREST = "INTEREST";
    static final String LINE_TYPE_TRANSFER = "TRANSFER";

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_POSTED = "POSTED";

    static final String ERR_UNBALANCED_LEGS = "unbalanced legs";
    static final String ERR_MISSING_LEGS = "at least two legs are required";
    static final String ERR_LINE_DTO_TYPE_MISMATCH = "lineDto does not match lineType";
    static final String ERR_NULL_TX_ID = "core-banking-ledger returned a transaction without an id";

    private final TransactionsApi transactionsApi;
    private final TransactionLegsApi transactionLegsApi;
    private final TransactionStatusHistoryApi transactionStatusHistoryApi;
    private final TransactionLineCardApi transactionLineCardApi;
    private final TransactionLineFeeApi transactionLineFeeApi;
    private final TransactionLineInterestApi transactionLineInterestApi;
    private final TransactionLineTransferApi transactionLineTransferApi;

    @Override
    protected Mono<PostLedgerTransactionResult> doHandle(PostLedgerTransactionCommand cmd) {
        return Mono.defer(() -> {
                    validateLegs(cmd.getLegs());
                    return createTransaction(cmd);
                })
                .flatMap(tx -> {
                    UUID transactionId = Objects.requireNonNull(tx.getTransactionId(), ERR_NULL_TX_ID);
                    return postLegs(transactionId, cmd.getLegs())
                            .then(postTypedLineIfPresent(transactionId, cmd))
                            .then(transitionStatusIfRequested(transactionId, cmd))
                            .thenReturn(transactionId);
                })
                .map(transactionId -> PostLedgerTransactionResult.builder()
                        .transactionId(transactionId)
                        .build())
                .doOnSuccess(result -> log.info(
                        "Posted ledger tx transactionId={} externalReference={} type={} status={}",
                        result.getTransactionId(),
                        cmd.getExternalReference(),
                        cmd.getTransactionType(),
                        resolveInitialStatus(cmd)))
                .doOnError(err -> log.warn(
                        "Failed to post ledger tx externalReference={} type={} error={}",
                        cmd.getExternalReference(),
                        cmd.getTransactionType(),
                        err.getMessage()));
    }

    private void validateLegs(List<LedgerLegSpec> legs) {
        if (legs == null || legs.size() < 2) {
            throw new IllegalArgumentException(ERR_MISSING_LEGS);
        }
        BigDecimal debits = legs.stream()
                .filter(l -> LEG_TYPE_DEBIT.equalsIgnoreCase(l.getLegType()))
                .map(LedgerLegSpec::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = legs.stream()
                .filter(l -> LEG_TYPE_CREDIT.equalsIgnoreCase(l.getLegType()))
                .map(LedgerLegSpec::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalArgumentException(ERR_UNBALANCED_LEGS);
        }
    }

    private Mono<TransactionDTO> createTransaction(PostLedgerTransactionCommand cmd) {
        TransactionDTO dto = new TransactionDTO();
        dto.setExternalReference(cmd.getExternalReference());
        dto.setTransactionType(TransactionDTO.TransactionTypeEnum.fromValue(cmd.getTransactionType()));
        dto.setTransactionStatus(TransactionDTO.TransactionStatusEnum.fromValue(STATUS_PENDING));
        dto.setTotalAmount(cmd.getTotalAmount());
        dto.setCurrency(cmd.getCurrency());
        dto.setValueDate(cmd.getValueDate());
        dto.setBookingDate(cmd.getBookingDate());
        dto.setTransactionDate(cmd.getValueDate());
        dto.setDescription(cmd.getDescription());
        dto.setRelatedTransactionId(cmd.getRelatedTransactionId());
        dto.setRelationType(cmd.getRelationType());
        return transactionsApi.createTransaction(dto, UUID.randomUUID().toString());
    }

    private Mono<Void> postLegs(UUID transactionId, List<LedgerLegSpec> legs) {
        return Flux.fromIterable(legs)
                .concatMap(leg -> transactionLegsApi.createTransactionLeg(
                        transactionId, toLegDto(transactionId, leg), UUID.randomUUID().toString()))
                .then();
    }

    private TransactionLegDTO toLegDto(UUID transactionId, LedgerLegSpec spec) {
        TransactionLegDTO leg = new TransactionLegDTO();
        leg.setTransactionId(transactionId);
        leg.setAccountId(spec.getAccountId());
        leg.setAccountSpaceId(spec.getAccountSpaceId());
        leg.setLegType(spec.getLegType());
        leg.setAmount(spec.getAmount());
        leg.setCurrency(spec.getCurrency());
        return leg;
    }

    private Mono<Void> postTypedLineIfPresent(UUID transactionId, PostLedgerTransactionCommand cmd) {
        String lineType = cmd.getLineType();
        Object lineDto = cmd.getLineDto();
        if (lineType == null || lineDto == null) {
            return Mono.empty();
        }
        String idempotencyKey = UUID.randomUUID().toString();
        return switch (lineType) {
            case LINE_TYPE_CARD -> transactionLineCardApi
                    .createCardLine(transactionId, castLine(lineDto, TransactionLineCardDTO.class), idempotencyKey)
                    .then();
            case LINE_TYPE_FEE -> transactionLineFeeApi
                    .createFeeLine(transactionId, castLine(lineDto, TransactionLineFeeDTO.class), idempotencyKey)
                    .then();
            case LINE_TYPE_INTEREST -> transactionLineInterestApi
                    .createInterestLine(transactionId, castLine(lineDto, TransactionLineInterestDTO.class), idempotencyKey)
                    .then();
            case LINE_TYPE_TRANSFER -> transactionLineTransferApi
                    .createTransferLine(transactionId, castLine(lineDto, TransactionLineTransferDTO.class), idempotencyKey)
                    .then();
            default -> Mono.error(new IllegalArgumentException(
                    "unsupported lineType: " + lineType + " — expected one of CARD|FEE|INTEREST|TRANSFER"));
        };
    }

    private <T> T castLine(Object lineDto, Class<T> expected) {
        if (!expected.isInstance(lineDto)) {
            throw new IllegalArgumentException(ERR_LINE_DTO_TYPE_MISMATCH
                    + ": expected " + expected.getSimpleName() + " but got " + lineDto.getClass().getSimpleName());
        }
        return expected.cast(lineDto);
    }

    private Mono<Void> transitionStatusIfRequested(UUID transactionId, PostLedgerTransactionCommand cmd) {
        if (!STATUS_POSTED.equalsIgnoreCase(resolveInitialStatus(cmd))) {
            return Mono.empty();
        }
        return transactionsApi
                .updateTransactionStatus(transactionId, STATUS_POSTED, cmd.getDescription(), UUID.randomUUID().toString())
                .then();
    }

    private String resolveInitialStatus(PostLedgerTransactionCommand cmd) {
        return cmd.getInitialStatus() == null ? STATUS_POSTED : cmd.getInitialStatus();
    }
}
