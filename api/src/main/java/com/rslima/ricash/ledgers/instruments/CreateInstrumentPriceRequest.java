package com.rslima.ricash.ledgers.instruments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInstrumentPriceRequest(
        @NotBlank String instrumentId,
        @NotNull @Positive BigDecimal price,
        @NotNull LocalDate effectiveDate
) {}
