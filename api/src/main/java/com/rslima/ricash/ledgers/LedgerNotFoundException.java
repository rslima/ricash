package com.rslima.ricash.ledgers;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class LedgerNotFoundException extends EntityNotFoundException {
    public LedgerNotFoundException(String ledger) {
        super("Ledger not found: " + ledger);
    }
}
