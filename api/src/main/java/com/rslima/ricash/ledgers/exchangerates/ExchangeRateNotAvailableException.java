package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.exceptions.EntityNotFoundException;

import java.time.LocalDate;

/**
 * Thrown when the external provider has no exchange rate for the requested
 * currency pair and date.
 */
public class ExchangeRateNotAvailableException extends EntityNotFoundException {
    public ExchangeRateNotAvailableException(String fromCurrency, String toCurrency, LocalDate date) {
        super("No exchange rate available from the external provider for %s -> %s on %s"
                .formatted(fromCurrency, toCurrency, date));
    }
}
