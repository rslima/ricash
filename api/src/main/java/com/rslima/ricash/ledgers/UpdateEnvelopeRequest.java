package com.rslima.ricash.ledgers;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateEnvelopeRequest(
        @NotBlank String name,
        String description,
        @NotNull EnvelopeType type,
        @NotBlank String currency,
        @NotNull EnvelopeStatus status,
        String parentEnvelopeId
) {
}
