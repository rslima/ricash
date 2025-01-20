package com.rslima.ricash.ledgers;

public record TransactionEntry(String accountId, TransactionEntryType type, MonetaryAmount amount, MonetaryAmount toAmount) {
}
