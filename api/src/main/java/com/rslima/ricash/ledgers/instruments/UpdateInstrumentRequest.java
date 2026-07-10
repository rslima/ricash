package com.rslima.ricash.ledgers.instruments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateInstrumentRequest(
        @NotBlank String symbol,
        @NotBlank String name,
        @NotNull InstrumentType type,
        @NotBlank String currency,
        String market,
        String isin,
        @NotNull InstrumentStatus status
) {}
