package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.ledgers.CurrencyCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FetchExchangeRateRequest(
        @NotBlank @Pattern(regexp = CurrencyCode.ANY_CASE, message = CurrencyCode.ANY_CASE_MESSAGE) String fromCurrency,
        @NotBlank @Pattern(regexp = CurrencyCode.ANY_CASE, message = CurrencyCode.ANY_CASE_MESSAGE) String toCurrency,
        @NotNull LocalDate date
) {}
