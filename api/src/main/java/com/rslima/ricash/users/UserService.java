package com.rslima.ricash.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserService {
    Page<User> listUsers(Pageable pageable);

    Optional<User> findUser(String id);

    User createUser(CreateUserRequest request);

    User updateUser(String id, UpdateUserRequest request);

    void deleteUser(String id);
}
