package com.rslima.ricash.ledgers.envelopes;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AllocateEnvelopeRequest(
        @NotNull(message = "Year is required")
        Integer year,

        @NotNull(message = "Month is required")
        Integer month,

        @NotNull(message = "Allocated amount is required")
        BigDecimal allocatedAmount,

        String notes
) {
}
