package com.rslima.ricash.ledgers.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single transaction within a report category drill-down. The amount is the
 * signed sum of only the entries belonging to the category's account subtree,
 * converted to the root category account's currency, so that the sum across all
 * category transactions equals the category total shown on the report.
 */
public record CategoryTransaction(
        String id,
        LocalDate date,
        String description,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
}
