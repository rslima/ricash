package com.rslima.ricash.ledgers.transactions;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Page<Transaction> listLedgerTransactions(String ledgerId, Pageable pageRequest);

    Page<Transaction> searchByDescription(String ledgerId, String description, Pageable pageRequest);

    Page<Transaction> listAccountTransactions(String ledgerId, String accountId, Pageable pageRequest);

    Page<Transaction> listCategoryTransactions(String ledgerId, String accountId, int year, int month, Pageable pageRequest);

    Page<CategoryTransaction> listCategoryTransactionAmounts(String ledgerId, String accountId, int year, int month, Pageable pageRequest);

    Optional<Transaction> findById(String ledgerId, String transactionId);

    Transaction create(String ledgerId, Transaction transaction);

    Transaction update(String ledgerId, Transaction transaction);

    void delete(String ledgerId, String transactionId);

    List<String> findDistinctDescriptions(String ledgerId);

    List<Transaction> findTransactionTemplates(String ledgerId);

    MonthlyReport getMonthlyReport(String ledgerId, int year, int month);

    MonthlyExpenseBreakdown getMonthlyExpenseBreakdown(String ledgerId, int year, int month);

    MonthlyIncomeBreakdown getMonthlyIncomeBreakdown(String ledgerId, int year, int month);
}
