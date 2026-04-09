package com.rslima.ricash.ledgers.transactions;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface TransactionService {
    Page<Transaction> listLedgerTransactions(String userId, String ledgerSlug, PageRequest pageRequest);

    Page<Transaction> searchByDescription(String userId, String ledgerSlug, String description, PageRequest pageRequest);

    Page<Transaction> listAccountTransactions(String userId, String ledgerSlug, String accountId, PageRequest pageRequest);

    Optional<Transaction> find(String userId, String ledgerSlug, String transactionId);

    Transaction create(String userId, String ledgerSlug, CreateTransactionRequest request);

    Transaction update(String userId, String ledgerSlug, String transactionId, UpdateTransactionRequest request);

    void delete(String userId, String ledgerSlug, String transactionId);

    List<String> getDistinctDescriptions(String userId, String ledgerSlug);

    List<Transaction> getTransactionTemplates(String userId, String ledgerSlug);

    MonthlyReport getMonthlyReport(String userId, String ledgerSlug, int year, int month);
}
