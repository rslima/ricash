package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class InstrumentNotFoundException extends EntityNotFoundException {
    public InstrumentNotFoundException(String id) {
        super("Instrument not found: " + id);
    }
}
