package com.rslima.ricash.ledgers.envelopes;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AllocateEnvelopeRequest(
        @NotNull(message = "Year is required")
        Integer year,

        @NotNull(message = "Month is required")
        Integer month,

        @NotNull(message = "Allocated amount is required")
        @Digits(integer = 18, fraction = 2, message = "Allocated amount must have at most 2 decimal places")
        BigDecimal allocatedAmount,

        String notes
) {
}
