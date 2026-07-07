package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class EnvelopeNotFoundException extends EntityNotFoundException {
    public EnvelopeNotFoundException(String envelopeId) {
        super("Envelope not found: " + envelopeId);
    }
}
