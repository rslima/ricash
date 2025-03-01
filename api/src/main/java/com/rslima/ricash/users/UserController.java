package com.rslima.ricash.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(final UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    Page<User> listUsers(@RequestParam Pageable pageable) {
        return userService.listUsers(pageable);
    }

    @GetMapping("/{id}")
    CleanedUser getUser(@PathVariable String id) {
        return userService.findUser(id).map(this::cleanedUser)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private CleanedUser cleanedUser(User u) {
        return new CleanedUser(u.id(), u.name(), u.email(), u.status(), u.createdAt(), u.ledgers(), u.roles());
    }
}
