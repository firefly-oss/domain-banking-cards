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


package io.fireflysoftware.domain.banking.cards.core.transaction.commands;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Command that drives {@code AccrueCardInterestSaga}. Typically invoked by the batch
 * scheduler to post the interest accrual for a credit-card statement period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccrueCardInterestCommand implements Command<UUID> {

    private UUID cardId;
    private BigDecimal interestAmount;
    private BigDecimal interestRate;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String calculationMethod;
}
