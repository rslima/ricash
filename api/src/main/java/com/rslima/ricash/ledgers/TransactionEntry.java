package com.rslima.ricash.ledgers;

import java.util.Optional;

public record TransactionEntry(String accountId, TransactionEntryType type, MonetaryAmount amount, Optional<MonetaryAmount> toAmount) {
}
