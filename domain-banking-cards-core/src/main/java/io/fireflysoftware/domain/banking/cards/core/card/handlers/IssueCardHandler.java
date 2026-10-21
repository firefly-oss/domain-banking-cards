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
import io.fireflysoftware.domain.banking.cards.core.card.commands.IssueCardCommand;
import io.fireflysoftware.domain.banking.cards.core.card.parameters.CardJourneyParameterPort;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds a fully-populated {@link CardDTO} and posts it to core-banking-cards.
 *
 * <p>Core-banking-cards enforces a strict required-field set on {@code CardDTO} that the
 * domain caller does not always know — the handler fills in the missing values with
 * domain-level defaults / derivations so a minimal {@link IssueCardCommand} still produces
 * a valid card record.
 *
 * <p>Fields supplied by the caller and passed through as-is:
 * <ul>
 *   <li>{@code customerId} / {@code partyId}</li>
 *   <li>{@code accountId}</li>
 *   <li>{@code cardProgramId} / {@code cardTypeId}</li>
 *   <li>{@code currency} / {@code currencyCode}</li>
 *   <li>{@code cardNetworkId}, {@code issuerId}, {@code binId}</li>
 *   <li>{@code cardHolderId}, {@code cardHolderName}</li>
 *   <li>form-factor flags: {@code isPhysical}, {@code isVirtual}, {@code isPrimary}</li>
 * </ul>
 *
 * <p>Fields derived / defaulted by the handler:
 * <ul>
 *   <li>{@code cardNumber} — a Luhn-valid number derived from the party and account.</li>
 *   <li>{@code expirationMonth} / {@code expirationYear} — defaulted to +4 years from today
 *       (typical card-network tenure).</li>
 *   <li>{@code cardHolderId} / {@code cardHolderName} — fall back to the party id when the
 *       caller does not supply them.</li>
 *   <li>{@code isActive} = true, {@code isLocked} = false on issuance.</li>
 * </ul>
 */
@CommandHandlerComponent
public class IssueCardHandler extends CommandHandler<IssueCardCommand, UUID> {

    private static final UUID DEFAULT_ISSUER = UUID.fromString("10000000-0000-4000-8000-0000000000c1");
    private static final UUID DEFAULT_NETWORK = UUID.fromString("10000000-0000-4000-8000-0000000000c2");
    private static final UUID DEFAULT_BIN = UUID.fromString("10000000-0000-4000-8000-0000000000c3");

    private final CardsApi cardsApi;
    private final CardJourneyParameterPort parameters;

    public IssueCardHandler(CardsApi cardsApi, CardJourneyParameterPort parameters) {
        this.cardsApi = cardsApi;
        this.parameters = parameters;
    }

    @Override
    protected Mono<UUID> doHandle(IssueCardCommand cmd) {
        return parameters.issuanceDefaults().flatMap(defaults -> issue(cmd, defaults));
    }

    private Mono<UUID> issue(IssueCardCommand cmd, CardJourneyParameterPort.CardIssuanceDefaults defaults) {
        CardDTO cardDTO = new CardDTO();

        // Caller-supplied identity.
        cardDTO.setPartyId(cmd.getCustomerId());
        cardDTO.setAccountId(cmd.getAccountId());
        cardDTO.setCardTypeId(cmd.getCardProgramId());
        cardDTO.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);

        // Caller-supplied issuing context.
        cardDTO.setCardNetworkId(cmd.getCardNetworkId() == null
                ? DEFAULT_NETWORK : cmd.getCardNetworkId());
        cardDTO.setIssuerId(cmd.getIssuerId() == null ? DEFAULT_ISSUER : cmd.getIssuerId());
        cardDTO.setBinId(cmd.getBinId() == null ? DEFAULT_BIN : cmd.getBinId());

        // Currency — prefer the caller value. Otherwise use the configured default, which is EUR in the sandbox.
        String currency = cmd.getCurrency() == null ? defaults.currencyCode() : cmd.getCurrency();
        cardDTO.setCurrencyCode(currency);

        // Cardholder — the domain layer accepts caller-supplied cardholder identity and
        // falls back to the partyId when the caller does not resolve it upstream. The
        // upstream schema models cardHolderId as a free-form string so we serialise the
        // partyId when we have to default it.
        cardDTO.setCardHolderId(cmd.getCardHolderId() == null
                ? (cmd.getCustomerId() == null ? null : cmd.getCustomerId().toString())
                : cmd.getCardHolderId());
        cardDTO.setCardHolderName(cmd.getCardHolderName() == null
                ? (cmd.getCustomerId() == null ? null : cmd.getCustomerId().toString())
                : cmd.getCardHolderName());
        cardDTO.setCardNumber(luhnPan(cmd.getCustomerId(), cmd.getAccountId()));

        LocalDate expiry = LocalDate.now().plusYears(defaults.tenureYears());
        cardDTO.setExpirationMonth(expiry.getMonthValue());
        cardDTO.setExpirationYear(expiry.getYear());

        // Form factor flags — default to physical primary card when the caller is silent.
        boolean physical = cmd.getIsPhysical() != null ? cmd.getIsPhysical() : true;
        boolean virtual = cmd.getIsVirtual() != null ? cmd.getIsVirtual() : false;
        boolean primary = cmd.getIsPrimary() != null ? cmd.getIsPrimary() : true;
        cardDTO.setIsPhysical(physical);
        cardDTO.setIsVirtual(virtual);
        cardDTO.setIsPrimary(primary);

        // Lifecycle flags — a newly-issued card is ACTIVE and unlocked by default; the
        // activate-card saga toggles activationDate later.
        cardDTO.setIsActive(true);
        cardDTO.setIsLocked(false);

        return cardsApi.createCard(cardDTO, UUID.randomUUID().toString())
                .map(CardDTO::getCardId);
    }

    /**
     * Builds a 16-digit Luhn number from the party and account so the same issue request
     * always receives the same card number.
     */
    private static String luhnPan(UUID customerId, UUID accountId) {
        long seed = (customerId == null ? 0L : customerId.getMostSignificantBits())
                ^ (accountId == null ? 0L : accountId.getLeastSignificantBits());
        long value = seed == Long.MIN_VALUE ? 0L : Math.abs(seed);
        int[] digits = new int[16];
        for (int i = 14; i >= 0; i--) {
            digits[i] = (int) (value % 10);
            value /= 10;
        }
        digits[15] = luhnCheckDigit(digits);
        StringBuilder sb = new StringBuilder(16);
        for (int digit : digits) {
            sb.append(digit);
        }
        return sb.toString();
    }

    /**
     * Computes the Luhn check digit for a 16-digit array where index 15 is the position the
     * check digit will occupy. Processes indices 0..14 right-to-left, doubling every second
     * digit starting from index 14.
     */
    private static int luhnCheckDigit(int[] digits) {
        int sum = 0;
        for (int i = 14; i >= 0; i--) {
            int d = digits[i];
            boolean doubleIt = ((14 - i) % 2) == 0;
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
        }
        return (10 - (sum % 10)) % 10;
    }
}
