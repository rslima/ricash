package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.CurrencyCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record CreateInstrumentRequest(
        @NotBlank String symbol,
        @NotBlank String name,
        @NotNull InstrumentType type,
        @NotBlank @Pattern(regexp = CurrencyCode.ANY_CASE, message = CurrencyCode.ANY_CASE_MESSAGE) String currency,
        String market,
        String isin
) {}
