package com.rslima.ricash.ledgers.transactions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateTransactionRequest(
        @NotNull LocalDate date,
        @NotBlank String description,
        @NotEmpty @Valid List<TransactionEntryRequest> entries
) {
}
