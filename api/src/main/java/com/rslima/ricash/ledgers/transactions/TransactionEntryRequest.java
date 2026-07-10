package com.rslima.ricash.ledgers.transactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * One double-entry leg of a create/update transaction request. Shared by
 * CreateTransactionRequest and UpdateTransactionRequest; the JSON shape is
 * identical for both.
 */
public record TransactionEntryRequest(
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
