package com.rslima.ricash.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository {
    Page<User> list(Pageable pageable);
    Optional<User> findById(String id);
}
