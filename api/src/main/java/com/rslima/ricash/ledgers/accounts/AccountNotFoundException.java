package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class AccountNotFoundException extends EntityNotFoundException {
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}
