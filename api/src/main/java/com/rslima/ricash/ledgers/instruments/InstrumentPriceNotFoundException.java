package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class InstrumentPriceNotFoundException extends EntityNotFoundException {
    public InstrumentPriceNotFoundException(String id) {
        super("Instrument price not found: " + id);
    }
}
