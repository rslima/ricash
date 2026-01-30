package com.rslima.ricash.ledgers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Represents a currency exchange rate.
 *
 * @param id unique identifier
 * @param fromCurrency source currency code (e.g., "USD")
 * @param toCurrency target currency code (e.g., "BRL")
 * @param rate the conversion rate (how much toCurrency equals 1 unit of fromCurrency)
 * @param effectiveDate the date this rate is effective
 * @param source the source of this rate (e.g., "API", "MANUAL", "BCB")
 * @param createdAt when this rate was recorded
 */
public record ExchangeRate(
    String id,
    String fromCurrency,
    String toCurrency,
    BigDecimal rate,
    LocalDate effectiveDate,
    String source,
    Instant createdAt
) {
}
