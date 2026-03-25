package com.rslima.ricash.ledgers;

public class AccountHasTransactionsException extends RuntimeException {
    public AccountHasTransactionsException(String accountId) {
        super("Cannot delete account " + accountId + " because it has associated transactions");
    }
}
