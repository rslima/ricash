package com.rslima.ricash.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceBeanTest {

    @Mock
    private UserRepository userRepository;

    private UserServiceBean userService;

    private static final String USER_ID = "user-id";

    @BeforeEach
    void setUp() {
        userService = new UserServiceBean(userRepository);
    }

    @Test
    void listUsers_delegatesToRepository() {
        var pageable = PageRequest.of(0, 20);
        var user = createTestUser();
        var page = new PageImpl<>(List.of(user), pageable, 1);

        when(userRepository.list(pageable)).thenReturn(page);

        var result = userService.listUsers(pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).list(pageable);
    }

    @Test
    void findUser_delegatesToRepository() {
        var user = createTestUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        var result = userService.findUser(USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(USER_ID);
    }

    @Test
    void createUser_delegatesToRepository() {
        var request = new CreateUserRequest("John", "john@example.com", "password123");
        var user = createTestUser();

        when(userRepository.create(request)).thenReturn(user);

        var result = userService.createUser(request);

        assertThat(result.name()).isEqualTo("John");
        verify(userRepository).create(request);
    }

    @Test
    void updateUser_existingUser() {
        var request = new UpdateUserRequest("Jane", "jane@example.com", UserStatus.ENABLED);
        var user = createTestUser();

        when(userRepository.update(USER_ID, request)).thenReturn(Optional.of(user));

        var result = userService.updateUser(USER_ID, request);

        assertThat(result).isNotNull();
    }

    @Test
    void updateUser_notFound_throws() {
        var request = new UpdateUserRequest("Jane", "jane@example.com", UserStatus.ENABLED);

        when(userRepository.update(USER_ID, request)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(USER_ID, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUser_existingUser() {
        when(userRepository.delete(USER_ID)).thenReturn(true);

        userService.deleteUser(USER_ID);

        verify(userRepository).delete(USER_ID);
    }

    @Test
    void deleteUser_notFound_throws() {
        when(userRepository.delete(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    private User createTestUser() {
        return new User(USER_ID, "John", "john@example.com", "hashed", "salt", UserStatus.ENABLED, Instant.now(), List.of());
    }
}
