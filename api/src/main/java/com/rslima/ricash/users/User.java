package com.rslima.ricash.users;

import java.time.Instant;
import java.util.List;

public record User(String id, String name, String email, String password, String salt, UserStatus status, Instant createdAt, List<String> ledgers, List<Role> roles) {
}
