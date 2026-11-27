package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.CurrencyCode;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateEnvelopeRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull EnvelopeType type,
        @NotBlank @CurrencyCode String currency,
        @NotNull EnvelopeStatus status,
        String parentEnvelopeId
) {
}
