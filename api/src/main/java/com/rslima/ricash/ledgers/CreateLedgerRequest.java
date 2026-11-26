package com.rslima.ricash.ledgers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLedgerRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotBlank(message = "Currency is required")
        @CurrencyCode
        String currency
) {
}
