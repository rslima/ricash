package com.rslima.ricash.ledgers.envelopes;

import java.math.BigDecimal;

public record EnvelopeBalance(
    String envelopeId,
    int periodYear,
    int periodMonth,
    BigDecimal rollover,
    BigDecimal allocated,
    BigDecimal spent,
    BigDecimal available
) {
}
