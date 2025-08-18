package com.rslima.ricash.users;

import com.rslima.ricash.ledgers.Ledger;

import java.time.Instant;
import java.util.List;

public record User(String id, String name, String email, String password, String salt, UserStatus status, Instant createdAt, List<Ledger> ledgers, List<Role> roles) {
}
