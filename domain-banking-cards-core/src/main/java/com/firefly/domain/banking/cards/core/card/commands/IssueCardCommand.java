package com.firefly.domain.banking.cards.core.card.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCardCommand implements Command<UUID> {

    // caller-supplied context
    private UUID customerId;
    private UUID accountId;
    private UUID cardProgramId;
    private String cardType;
    private String currency;

    // issuing context required by core-banking-cards. These are known by the
    // domain caller (issuer / BIN / network come from the card program config
    // or the issuing bank's setup).
    private UUID cardNetworkId;
    private UUID issuerId;
    private UUID binId;

    // cardholder identity — the customer service owns these, but the domain
    // caller typically passes them through because it is initiating the issue.
    // cardHolderId is a free-form identifier (string) in the upstream core schema.
    private String cardHolderName;
    private String cardHolderId;

    // form factor flags — defaulted by the handler when null.
    private Boolean isPhysical;
    private Boolean isVirtual;
    private Boolean isPrimary;
}
