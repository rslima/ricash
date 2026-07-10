package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.exceptions.ConflictException;

public class AccountHasTransactionsException extends ConflictException {
    public AccountHasTransactionsException(String accountId) {
        super("Cannot delete account " + accountId + " because it has associated transactions");
    }
}
