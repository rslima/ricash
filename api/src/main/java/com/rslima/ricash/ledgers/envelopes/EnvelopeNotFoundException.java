package com.rslima.ricash.ledgers.envelopes;

public class EnvelopeNotFoundException extends RuntimeException {
    public EnvelopeNotFoundException(String envelopeId) {
        super("Envelope not found: " + envelopeId);
    }
}
