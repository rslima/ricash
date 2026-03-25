package com.rslima.ricash.ledgers;

public class EnvelopeNotFoundException extends RuntimeException {
    public EnvelopeNotFoundException(String envelopeId) {
        super("Envelope not found: " + envelopeId);
    }
}
