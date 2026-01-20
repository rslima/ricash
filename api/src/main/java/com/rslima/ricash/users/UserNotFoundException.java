package com.rslima.ricash.users;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class UserNotFoundException extends EntityNotFoundException {
    public UserNotFoundException(String id) {
        super("User not found with id " + id);
    }
}
