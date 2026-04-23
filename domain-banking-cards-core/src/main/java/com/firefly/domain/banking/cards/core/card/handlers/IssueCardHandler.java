package com.firefly.domain.banking.cards.core.card.handlers;

import com.firefly.core.banking.cards.sdk.api.CardsApi;
import com.firefly.core.banking.cards.sdk.model.CardDTO;
import com.firefly.domain.banking.cards.core.card.commands.IssueCardCommand;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
 *   <li>{@code cardNumber} — generated as a 16-digit placeholder. A real issuer flow would
 *       delegate PAN generation to the card-network adapter; that integration is out of scope
 *       for the domain layer.</li>
 *   <li>{@code expirationMonth} / {@code expirationYear} — defaulted to +4 years from today
 *       (typical card-network tenure).</li>
 *   <li>{@code cardHolderId} / {@code cardHolderName} — fall back to the partyId and a
 *       "CARDHOLDER" placeholder when the caller does not supply them. A richer flow would
 *       resolve these via the customer SDK.</li>
 *   <li>{@code isActive} = true, {@code isLocked} = false on issuance.</li>
 * </ul>
 */
@CommandHandlerComponent
public class IssueCardHandler extends CommandHandler<IssueCardCommand, UUID> {

    private static final int DEFAULT_CARD_TENURE_YEARS = 4;

    private final CardsApi cardsApi;

    public IssueCardHandler(CardsApi cardsApi) {
        this.cardsApi = cardsApi;
    }

    @Override
    protected Mono<UUID> doHandle(IssueCardCommand cmd) {
        CardDTO cardDTO = new CardDTO();

        // Caller-supplied identity.
        cardDTO.setPartyId(cmd.getCustomerId());
        cardDTO.setAccountId(cmd.getAccountId());
        cardDTO.setCardTypeId(cmd.getCardProgramId());
        cardDTO.setCardStatus(CardDTO.CardStatusEnum.ACTIVE);

        // Caller-supplied issuing context.
        cardDTO.setCardNetworkId(cmd.getCardNetworkId());
        cardDTO.setIssuerId(cmd.getIssuerId());
        cardDTO.setBinId(cmd.getBinId());

        // Currency — prefer explicit currency on the command, fall back to EUR for the
        // Firefly reference bank.
        cardDTO.setCurrencyCode(cmd.getCurrency() == null ? "EUR" : cmd.getCurrency());

        // Cardholder — the domain layer accepts caller-supplied cardholder identity and
        // falls back to the partyId when the caller does not resolve it upstream. The
        // upstream schema models cardHolderId as a free-form string so we serialise the
        // partyId when we have to default it.
        cardDTO.setCardHolderId(cmd.getCardHolderId() == null
                ? (cmd.getCustomerId() == null ? null : cmd.getCustomerId().toString())
                : cmd.getCardHolderId());
        cardDTO.setCardHolderName(cmd.getCardHolderName() == null ? "CARDHOLDER" : cmd.getCardHolderName());

        // PAN — the real PAN is assigned by the card-network adapter. The domain layer
        // synthesises a placeholder so the create-card call satisfies the required-field
        // validation; the integration layer is expected to overwrite it on personalisation.
        cardDTO.setCardNumber(generatePlaceholderPan());

        // Expiration — +4 years from today.
        LocalDate expiry = LocalDate.now().plusYears(DEFAULT_CARD_TENURE_YEARS);
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
     * Generates a 16-digit numeric placeholder that passes the Luhn checksum required by
     * core-banking-cards' {@code cardNumber} validator. This is <strong>not</strong> a real
     * PAN — a production issuer flow must replace this with a generator that honours the
     * network's BIN range.
     */
    private static String generatePlaceholderPan() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int[] digits = new int[16];
        for (int i = 0; i < 15; i++) {
            digits[i] = rnd.nextInt(10);
        }
        digits[15] = luhnCheckDigit(digits);
        StringBuilder sb = new StringBuilder(16);
        for (int d : digits) {
            sb.append(d);
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
