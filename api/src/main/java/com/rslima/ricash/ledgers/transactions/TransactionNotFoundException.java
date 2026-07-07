package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.exceptions.EntityNotFoundException;

public class TransactionNotFoundException extends EntityNotFoundException {
    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
