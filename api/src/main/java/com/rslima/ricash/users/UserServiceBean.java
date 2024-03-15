package com.rslima.ricash.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class UserServiceBean implements UserService {
    @Override
    public Page<User> listUsers(Pageable pageable) {
        return new PageImpl<>(List.of(), pageable, 0);
    }
}
