package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.exceptions.ConflictException;

public class EnvelopeHasTransactionsException extends ConflictException {
    public EnvelopeHasTransactionsException(String envelopeId) {
        super("Cannot delete envelope with associated transactions: " + envelopeId);
    }
}
