package com.rslima.ricash.ledgers;

public class EnvelopeHasTransactionsException extends RuntimeException {
    public EnvelopeHasTransactionsException(String envelopeId) {
        super("Cannot delete envelope with associated transactions: " + envelopeId);
    }
}
