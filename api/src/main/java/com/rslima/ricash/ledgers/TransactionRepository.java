package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface TransactionRepository {
    Page<Transaction> listLedgerTransactions(String ledgerId, PageRequest pageRequest);

    Page<Transaction> listAccountTransactions(String ledgerId, String accountId, PageRequest pageRequest);

    Optional<Transaction> findById(String ledgerId, String transactionId);

    Transaction create(String ledgerId, Transaction transaction);

    Transaction update(String ledgerId, Transaction transaction);

    void delete(String ledgerId, String transactionId);
}
