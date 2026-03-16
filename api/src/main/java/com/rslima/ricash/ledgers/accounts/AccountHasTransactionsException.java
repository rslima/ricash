package com.rslima.ricash.ledgers.accounts;

public class AccountHasTransactionsException extends RuntimeException {
    public AccountHasTransactionsException(String accountId) {
        super("Cannot delete account " + accountId + " because it has associated transactions");
    }
}
