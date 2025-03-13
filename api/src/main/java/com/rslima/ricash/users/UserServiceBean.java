package com.rslima.ricash.users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class UserServiceBean implements UserService {

    private final UserRepository userRepository;

    @Override
    public Page<User> listUsers(Pageable pageable) {
        return userRepository.list(pageable);
    }

    @Override
    public Optional<User> findUser(String id) {
        return userRepository.findById(id);
    }

    @Override
    public User createUser(User user) {
        return user;
    }


}
