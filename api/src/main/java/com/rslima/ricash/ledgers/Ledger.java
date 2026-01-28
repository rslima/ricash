package com.rslima.ricash.ledgers;

import java.time.Instant;
import java.util.List;

public record Ledger(String id, String userId, String name, String description, String currency, Instant createdAt, List<Account> accounts, List<Transaction> transactions) {
}
