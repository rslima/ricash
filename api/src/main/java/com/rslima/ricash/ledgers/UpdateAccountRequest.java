package com.rslima.ricash.ledgers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(
        @NotBlank String name,
        String description,
        @NotNull AccountType type,
        @NotBlank String currency,
        String parentAccountId
) {}
