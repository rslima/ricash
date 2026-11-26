package com.rslima.ricash.ledgers;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record UpdateLedgerRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description
) {}
