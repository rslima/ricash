package com.rslima.ricash.ledgers.exchangerates;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FetchExchangeRateRequest(
        @NotBlank String fromCurrency,
        @NotBlank String toCurrency,
        @NotNull LocalDate date
) {}
