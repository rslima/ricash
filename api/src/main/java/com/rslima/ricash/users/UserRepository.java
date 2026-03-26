package com.rslima.ricash.users;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String id);

    User create(String id);
}
