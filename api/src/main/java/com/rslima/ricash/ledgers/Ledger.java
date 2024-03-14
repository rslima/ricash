package com.rslima.ricash.ledgers;

import com.rslima.ricash.accounts.Account;

import java.time.Instant;
import java.util.List;

public record Ledger(String id, String name, String description, String currency, Instant createdAt, List<Account> accounts, List<Transaction> transactions) {
}
