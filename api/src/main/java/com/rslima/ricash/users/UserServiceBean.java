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
    public User createUser(CreateUserRequest request) {
        log.debug("Creating user with email: {}", request.email());
        return userRepository.create(request);
    }

    @Override
    public User updateUser(String id, UpdateUserRequest request) {
        log.debug("Updating user: {}", id);
        return userRepository.update(id, request)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Override
    public void deleteUser(String id) {
        log.debug("Deleting user: {}", id);
        if (!userRepository.delete(id)) {
            throw new UserNotFoundException(id);
        }
    }
}
