package com.rslima.ricash.ledgers.exchangerates;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateExchangeRateRequest(
        @NotBlank String fromCurrency,
        @NotBlank String toCurrency,
        @NotNull @Positive BigDecimal rate,
        @NotNull LocalDate effectiveDate
) {}
