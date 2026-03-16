package com.rslima.ricash.ledgers.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Account(String id, String slug, String name, String description, String currency, AccountType type, AccountStatus status, BigDecimal balance, Instant createdAt, String parentAccountId, List<Account> subAccounts) {
}
