package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.ledgers.CurrencyCode;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull AccountType type,
        @NotBlank @CurrencyCode String currency,
        String parentAccountId
) {}
