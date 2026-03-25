package com.rslima.ricash.ledgers;

import java.math.BigDecimal;
import java.time.Instant;

public record EnvelopeAllocation(
    String id,
    String envelopeId,
    int periodYear,
    int periodMonth,
    BigDecimal allocatedAmount,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
}
