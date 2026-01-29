package com.rslima.ricash.ledgers;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String transactionId) {
        super("Transaction not found: " + transactionId);
    }
}
