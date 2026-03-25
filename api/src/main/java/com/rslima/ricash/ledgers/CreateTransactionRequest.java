package com.rslima.ricash.ledgers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateTransactionRequest(
        @NotNull LocalDate date,
        @NotBlank String description,
        @NotEmpty @Valid List<EntryRequest> entries
) {
    public record EntryRequest(
            @NotBlank String accountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank String currency,
            @Positive BigDecimal toAmount,
            String toCurrency,
            @NotNull TransactionEntryType type,
            String instrumentId,
            @Positive BigDecimal quantity,
            String envelopeId
    ) {}
}
