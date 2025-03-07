package com.rslima.ricash.ledgers;

public class LedgerNotFoundException extends RuntimeException {
    public LedgerNotFoundException(String ledger) {
        super("Ledger not found: " + ledger);
    }
}
