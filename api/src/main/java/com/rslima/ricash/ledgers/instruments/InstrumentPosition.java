package com.rslima.ricash.ledgers.instruments;

import java.math.BigDecimal;

/**
 * Represents a calculated position in an instrument for a specific account.
 *
 * @param instrumentId the instrument ID
 * @param instrumentSymbol the instrument symbol
 * @param instrumentName the instrument name
 * @param instrumentType the type of instrument
 * @param currency the currency
 * @param quantity the current quantity held
 * @param totalCost the total cost of acquisition
 * @param averageCost the average cost per unit
 * @param currentPrice the latest price (if available)
 * @param currentValue the current market value (quantity * currentPrice)
 * @param unrealizedGain the unrealized gain/loss (currentValue - totalCost)
 * @param unrealizedGainPercent the unrealized gain/loss as a percentage
 */
public record InstrumentPosition(
    String instrumentId,
    String instrumentSymbol,
    String instrumentName,
    InstrumentType instrumentType,
    String currency,
    BigDecimal quantity,
    BigDecimal totalCost,
    BigDecimal averageCost,
    BigDecimal currentPrice,
    BigDecimal currentValue,
    BigDecimal unrealizedGain,
    BigDecimal unrealizedGainPercent
) {
}
