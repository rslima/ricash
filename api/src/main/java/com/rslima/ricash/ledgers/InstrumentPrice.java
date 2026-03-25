package com.rslima.ricash.ledgers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a historical price point for an instrument.
 *
 * @param id unique identifier
 * @param instrumentId the instrument this price belongs to
 * @param price the price value
 * @param effectiveDate the date this price is effective
 * @param source the source of this price (e.g., "B3", "MANUAL", "API")
 * @param createdAt when this price was recorded
 */
public record InstrumentPrice(
    String id,
    String instrumentId,
    BigDecimal price,
    LocalDate effectiveDate,
    String source,
    Instant createdAt
) {
}
