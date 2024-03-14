package com.rslima.ricash.accounts;

import java.time.Instant;
import java.util.List;

public record Account(String id, String name, String description, String currency, AccountType type, AccountStatus status, Instant createdAt, List<Account> subAccounts) {
}
