package com.rslima.ricash.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    Page<User> listUsers(Pageable pageable);
}
