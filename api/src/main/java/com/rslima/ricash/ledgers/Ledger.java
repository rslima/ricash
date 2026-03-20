package com.rslima.ricash.ledgers;

import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.transactions.Transaction;

import java.time.Instant;
import java.util.List;

public record Ledger(String id, String userId, String slug, String name, String description, String currency, Instant createdAt, List<Account> accounts, List<Transaction> transactions) {
}
