package com.rslima.ricash.users;

import java.time.Instant;
import java.util.List;

public record CleanedUser(
        String id,
        String name,
        String email,
        UserStatus status,
        Instant createdAt,
        List<String> ledgers,
        List<Role> roles) {
}
