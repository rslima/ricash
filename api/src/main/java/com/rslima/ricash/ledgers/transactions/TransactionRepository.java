package com.rslima.ricash.ledgers.transactions;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * Every entry of every transaction that touches one of {@code accountIds}
     * (null or empty = whole ledger) within {@code [from, toExclusive)} (either
     * bound nullable = unbounded), flattened one row per entry and ordered by
     * date, creation time, transaction id and entry id ascending. Transactions
     * are returned whole: counterpart entries outside the account scope are
     * included.
     */
    List<TransactionExportRow> listEntriesForExport(String ledgerId, Collection<String> accountIds,
                                                    LocalDate from, LocalDate toExclusive);

    /**
     * Signed balance (debits minus credits, in each account's own currency) per
     * account over all transactions before {@code toExclusive} (null = all time).
     * Accounts without any entries are absent from the map.
     */
    Map<String, BigDecimal> getAccountBalancesAsOf(String ledgerId, Collection<String> accountIds,
                                                   LocalDate toExclusive);

    List<String> findDistinctDescriptions(String ledgerId);

    List<Transaction> findTransactionTemplates(String ledgerId);

    MonthlyReport getMonthlyReport(String ledgerId, int year, int month);

    MonthlyExpenseBreakdown getMonthlyExpenseBreakdown(String ledgerId, int year, int month);

    MonthlyIncomeBreakdown getMonthlyIncomeBreakdown(String ledgerId, int year, int month);
}
