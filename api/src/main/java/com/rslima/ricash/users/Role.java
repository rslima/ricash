package com.rslima.ricash.users;

import java.time.Instant;

public record Role(String id, String name, String description, Instant createdAt) {
}
