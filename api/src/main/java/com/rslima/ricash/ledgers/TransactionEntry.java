package com.rslima.ricash.ledgers;

import java.math.BigDecimal;

public record TransactionEntry(String accountId, BigDecimal amount, String currency) {
}
