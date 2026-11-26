package com.rslima.ricash.ledgers.transactions;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * One double-entry leg of a create/update transaction request. Shared by
 * CreateTransactionRequest and UpdateTransactionRequest; the JSON shape is
 * identical for both.
 *
 * <p>The {@code @Digits} limits mirror the column types (amounts
 * {@code numeric(20,2)}, quantity {@code numeric(20,8)}). Without them
 * Postgres would round silently after the balance check ran on the unrounded
 * values: debits 0.005 + 0.005 vs a credit of 0.01 pass, then store as
 * 0.01 + 0.01 vs 0.01.
 */
public record TransactionEntryRequest(
        @NotBlank String accountId,
        @NotNull @Positive @Digits(integer = 18, fraction = 2) BigDecimal amount,
        @NotBlank String currency,
        @Positive @Digits(integer = 18, fraction = 2) BigDecimal toAmount,
        String toCurrency,
        @NotNull TransactionEntryType type,
        String instrumentId,
        @Positive @Digits(integer = 12, fraction = 8) BigDecimal quantity,
        String envelopeId
) {}
