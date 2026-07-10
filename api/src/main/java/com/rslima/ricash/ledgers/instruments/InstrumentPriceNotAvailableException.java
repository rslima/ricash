package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.exceptions.EntityNotFoundException;

/**
 * Thrown when the external provider has no usable price for the requested
 * instrument's ISIN (unresolvable, unavailable, or no currency-matching listing).
 */
public class InstrumentPriceNotAvailableException extends EntityNotFoundException {
    public InstrumentPriceNotAvailableException(String symbol, String isin) {
        super("No price available from the external provider for instrument %s (ISIN %s)"
                .formatted(symbol, isin));
    }
}
