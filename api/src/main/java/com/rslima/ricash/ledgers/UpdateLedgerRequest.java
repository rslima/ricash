package com.rslima.ricash.ledgers;

import jakarta.validation.constraints.NotBlank;

public record UpdateLedgerRequest(
        @NotBlank String name,
        String description
) {}
