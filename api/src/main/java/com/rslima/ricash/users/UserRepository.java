package com.rslima.ricash.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository {
    Page<User> list(Pageable pageable);

    Optional<User> findById(String id);

    User create(CreateUserRequest request);

    Optional<User> update(String id, UpdateUserRequest request);

    boolean delete(String id);
}
