package com.rslima.ricash.ledgers;

import java.time.Instant;
import java.util.List;

public record Envelope(
    String id,
    String name,
    String description,
    String currency,
    EnvelopeType type,
    EnvelopeStatus status,
    Instant createdAt,
    String parentEnvelopeId,
    List<Envelope> subEnvelopes
) {
}
