package com.rslima.ricash.ledgers.exchangerates;

import java.time.LocalDate;

/**
 * Thrown when the external provider has no exchange rate for the requested
 * currency pair and date.
 */
public class ExchangeRateNotAvailableException extends RuntimeException {
    public ExchangeRateNotAvailableException(String fromCurrency, String toCurrency, LocalDate date) {
        super("No exchange rate available from the external provider for %s -> %s on %s"
                .formatted(fromCurrency, toCurrency, date));
    }
}
