package com.rslima.ricash.ledgers.transactions;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Page<Transaction> listLedgerTransactions(String ledgerId, PageRequest pageRequest);

    Page<Transaction> searchByDescription(String ledgerId, String description, PageRequest pageRequest);

    Page<Transaction> listAccountTransactions(String ledgerId, String accountId, PageRequest pageRequest);

    Optional<Transaction> findById(String ledgerId, String transactionId);

    Transaction create(String ledgerId, Transaction transaction);

    Transaction update(String ledgerId, Transaction transaction);

    void delete(String ledgerId, String transactionId);

    List<String> findDistinctDescriptions(String ledgerId);

    List<Transaction> findTransactionTemplates(String ledgerId);

    MonthlyReport getMonthlyReport(String ledgerId, int year, int month);

    MonthlyExpenseBreakdown getMonthlyExpenseBreakdown(String ledgerId, int year, int month);
}
