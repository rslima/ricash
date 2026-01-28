package com.rslima.ricash.ledgers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateLedgerRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
        String name,

        String description,

        @NotBlank(message = "Currency is required")
        @Size(min = 1, max = 50, message = "Currency must be between 1 and 50 characters")
        String currency
) {
}
