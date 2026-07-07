package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.ledgers.MonetaryAmount;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.LinkedHashMap;

import static com.rslima.ricash.ledgers.DateRanges.monthEnd;
import static com.rslima.ricash.ledgers.DateRanges.monthStart;
import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
@Slf4j
public class TransactionJdbcRepository implements TransactionRepository {
    private final JdbcClient jdbcClient;

    record DBTransaction(String id, String ledgerId, LocalDate date, String description, Instant createdAt) {}

    record DBTransactionWithEntry(String transactionId, LocalDate date, String description, Instant createdAt,
                                   String entryId, String accountId, String accountName,
                                   BigDecimal amount, String type, String currency,
                                   BigDecimal toAmount, String toCurrency,
                                   String instrumentId, BigDecimal quantity, String instrumentSymbol,
                                   String envelopeId) {}

    @Override
    public Page<Transaction> listLedgerTransactions(String ledgerId, PageRequest pageRequest) {
        final var results = jdbcClient.sql("""
                        SELECT
                            t.id AS transaction_id,
                            t.date,
                            t.description,
                            t.created_at,
                            te.id AS entry_id,
                            te.account_id,
                            a.name AS account_name,
                            te.amount,
                            te.type,
                            te.currency,
                            te.to_amount,
                            te.to_currency,
                            te.instrument_id,
                            te.quantity,
                            i.symbol AS instrument_symbol,
                            te.envelope_id
                        FROM
                            (SELECT * FROM transactions WHERE ledger_id = :ledgerId
                             ORDER BY date DESC, created_at DESC
                             OFFSET :offset LIMIT :limit) t
                        LEFT JOIN transaction_entries te ON t.id = te.transaction_id
                        LEFT JOIN accounts a ON te.account_id = a.id
                        LEFT JOIN instruments i ON te.instrument_id = i.id
                        ORDER BY t.date DESC, t.created_at DESC
                        """)
                .param("ledgerId", ledgerId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBTransactionWithEntry.class)
                .list();

        final var total = jdbcClient.sql("SELECT COUNT(*) FROM transactions WHERE ledger_id = :ledgerId")
                .param("ledgerId", ledgerId)
                .query(Long.class)
                .single();

        final var transactions = groupToTransactions(results);
        return new PageImpl<>(transactions, pageRequest, total);
    }

    @Override
    public Page<Transaction> searchByDescription(String ledgerId, String description, PageRequest pageRequest) {
        final var results = jdbcClient.sql("""
                        SELECT
                            t.id AS transaction_id,
                            t.date,
                            t.description,
                            t.created_at,
                            te.id AS entry_id,
                            te.account_id,
                            a.name AS account_name,
                            te.amount,
                            te.type,
                            te.currency,
                            te.to_amount,
                            te.to_currency,
                            te.instrument_id,
                            te.quantity,
                            i.symbol AS instrument_symbol,
                            te.envelope_id
                        FROM
                            (SELECT * FROM transactions WHERE ledger_id = :ledgerId
                             AND description ILIKE :description
                             ORDER BY date DESC, created_at DESC
                             OFFSET :offset LIMIT :limit) t
                        LEFT JOIN transaction_entries te ON t.id = te.transaction_id
                        LEFT JOIN accounts a ON te.account_id = a.id
                        LEFT JOIN instruments i ON te.instrument_id = i.id
                        ORDER BY t.date DESC, t.created_at DESC
                        """)
                .param("ledgerId", ledgerId)
                .param("description", "%" + description + "%")
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBTransactionWithEntry.class)
                .list();

        final var total = jdbcClient.sql("""
                        SELECT COUNT(*) FROM transactions
                        WHERE ledger_id = :ledgerId AND description ILIKE :description
                        """)
                .param("ledgerId", ledgerId)
                .param("description", "%" + description + "%")
                .query(Long.class)
                .single();

        final var transactions = groupToTransactions(results);
        return new PageImpl<>(transactions, pageRequest, total);
    }

    @Override
    public Page<Transaction> listAccountTransactions(String ledgerId, String accountId, PageRequest pageRequest) {
        final var results = jdbcClient.sql("""
                        SELECT
                            t.id AS transaction_id,
                            t.date,
                            t.description,
                            t.created_at,
                            te.id AS entry_id,
                            te.account_id,
                            a.name AS account_name,
                            te.amount,
                            te.type,
                            te.currency,
                            te.to_amount,
                            te.to_currency,
                            te.instrument_id,
                            te.quantity,
                            i.symbol AS instrument_symbol,
                            te.envelope_id
                        FROM
                            (SELECT DISTINCT t.* FROM transactions t
                             INNER JOIN transaction_entries te ON t.id = te.transaction_id
                             WHERE t.ledger_id = :ledgerId AND te.account_id = :accountId
                             ORDER BY t.date DESC, t.created_at DESC
                             OFFSET :offset LIMIT :limit) t
                        LEFT JOIN transaction_entries te ON t.id = te.transaction_id
                        LEFT JOIN accounts a ON te.account_id = a.id
                        LEFT JOIN instruments i ON te.instrument_id = i.id
                        ORDER BY t.date DESC, t.created_at DESC
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBTransactionWithEntry.class)
                .list();

        final var total = jdbcClient.sql("""
                        SELECT COUNT(DISTINCT t.id) FROM transactions t
                        INNER JOIN transaction_entries te ON t.id = te.transaction_id
                        WHERE t.ledger_id = :ledgerId AND te.account_id = :accountId
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .query(Long.class)
                .single();

        final var transactions = groupToTransactions(results);
        return new PageImpl<>(transactions, pageRequest, total);
    }

    @Override
    public Page<Transaction> listCategoryTransactions(String ledgerId, String accountId, int year, int month, PageRequest pageRequest) {
        // Collect the target account plus all of its descendants via the
        // parent_account_id tree, then return every transaction in the given
        // month that touches any account in that subtree.
        final var results = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            SELECT id FROM accounts
                            WHERE id = :accountId AND ledger_id = :ledgerId

                            UNION ALL

                            SELECT a.id FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
                        )
                        SELECT
                            t.id AS transaction_id,
                            t.date,
                            t.description,
                            t.created_at,
                            te.id AS entry_id,
                            te.account_id,
                            a.name AS account_name,
                            te.amount,
                            te.type,
                            te.currency,
                            te.to_amount,
                            te.to_currency,
                            te.instrument_id,
                            te.quantity,
                            i.symbol AS instrument_symbol,
                            te.envelope_id
                        FROM
                            (SELECT DISTINCT t.* FROM transactions t
                             INNER JOIN transaction_entries te ON t.id = te.transaction_id
                             WHERE t.ledger_id = :ledgerId
                               AND te.account_id IN (SELECT id FROM account_tree)
                               AND t.date >= :periodStart
                               AND t.date < :periodEnd
                             ORDER BY t.date DESC, t.created_at DESC
                             OFFSET :offset LIMIT :limit) t
                        LEFT JOIN transaction_entries te ON t.id = te.transaction_id
                        LEFT JOIN accounts a ON te.account_id = a.id
                        LEFT JOIN instruments i ON te.instrument_id = i.id
                        ORDER BY t.date DESC, t.created_at DESC
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .param("periodStart", monthStart(year, month))
                .param("periodEnd", monthEnd(year, month))
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBTransactionWithEntry.class)
                .list();

        final var total = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            SELECT id FROM accounts
                            WHERE id = :accountId AND ledger_id = :ledgerId

                            UNION ALL

                            SELECT a.id FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
                        )
                        SELECT COUNT(DISTINCT t.id) FROM transactions t
                        INNER JOIN transaction_entries te ON t.id = te.transaction_id
                        WHERE t.ledger_id = :ledgerId
                          AND te.account_id IN (SELECT id FROM account_tree)
                          AND t.date >= :periodStart
                          AND t.date < :periodEnd
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .param("periodStart", monthStart(year, month))
                .param("periodEnd", monthEnd(year, month))
                .query(Long.class)
                .single();

        final var transactions = groupToTransactions(results);
        return new PageImpl<>(transactions, pageRequest, total);
    }

    record DBCategoryTransactionRow(String id, LocalDate date, String description, BigDecimal amount, Instant createdAt) {}

    @Override
    public Page<CategoryTransaction> listCategoryTransactionAmounts(String ledgerId, String accountId, int year, int month, PageRequest pageRequest) {
        // For each transaction touching the category subtree (the account plus
        // all descendants) in the given month, sum ONLY the entries whose
        // account is in the subtree. Sign is keyed off the ROOT account's type
        // (DEBIT positive for EXPENSE, CREDIT positive for INCOME) so values are
        // positive like the report breakdown, and amounts are converted to the
        // root account's currency. The per-transaction sums therefore add up to
        // the category total shown on the report.
        final var root = jdbcClient.sql("""
                        SELECT type, currency FROM accounts
                        WHERE id = :accountId AND ledger_id = :ledgerId
                        """)
                .param("accountId", accountId)
                .param("ledgerId", ledgerId)
                .query(DBRootAccount.class)
                .optional();

        if (root.isEmpty()) {
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        final boolean incomeSign = "INCOME".equals(root.get().type());

        final var rows = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            SELECT id, currency FROM accounts
                            WHERE id = :accountId AND ledger_id = :ledgerId

                            UNION ALL

                            SELECT a.id, a.currency FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
                        ),
                        root_account AS (
                            SELECT currency FROM accounts
                            WHERE id = :accountId AND ledger_id = :ledgerId
                        )
                        SELECT
                            t.id AS id,
                            t.date,
                            t.description,
                            t.created_at,
                            SUM(
                                (CASE WHEN te.type = :positiveType THEN 1 ELSE -1 END) *
                                (CASE
                                    WHEN te.to_currency = (SELECT currency FROM root_account) THEN te.to_amount
                                    WHEN te.currency = (SELECT currency FROM root_account) THEN te.amount
                                    ELSE 0
                                END)
                            ) AS amount
                        FROM transactions t
                        INNER JOIN transaction_entries te ON t.id = te.transaction_id
                        WHERE t.ledger_id = :ledgerId
                          AND te.account_id IN (SELECT id FROM account_tree)
                          AND t.date >= :periodStart
                          AND t.date < :periodEnd
                        GROUP BY t.id, t.date, t.description, t.created_at
                        ORDER BY t.date DESC, t.created_at DESC
                        OFFSET :offset LIMIT :limit
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .param("positiveType", incomeSign ? "CREDIT" : "DEBIT")
                .param("periodStart", monthStart(year, month))
                .param("periodEnd", monthEnd(year, month))
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBCategoryTransactionRow.class)
                .list();

        final var total = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            SELECT id FROM accounts
                            WHERE id = :accountId AND ledger_id = :ledgerId

                            UNION ALL

                            SELECT a.id FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
                        )
                        SELECT COUNT(DISTINCT t.id) FROM transactions t
                        INNER JOIN transaction_entries te ON t.id = te.transaction_id
                        WHERE t.ledger_id = :ledgerId
                          AND te.account_id IN (SELECT id FROM account_tree)
                          AND t.date >= :periodStart
                          AND t.date < :periodEnd
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .param("periodStart", monthStart(year, month))
                .param("periodEnd", monthEnd(year, month))
                .query(Long.class)
                .single();

        final var rootCurrency = root.get().currency();
        final var categoryTransactions = rows.stream()
                .map(r -> new CategoryTransaction(r.id(), r.date(), r.description(), r.amount(), rootCurrency, r.createdAt()))
                .toList();

        return new PageImpl<>(categoryTransactions, pageRequest, total);
    }

    record DBRootAccount(String type, String currency) {}

    @Override
    public Optional<Transaction> findById(String ledgerId, String transactionId) {
        final var results = jdbcClient.sql("""
                        SELECT
                            t.id AS transaction_id,
                            t.date,
                            t.description,
                            t.created_at,
                            te.id AS entry_id,
                            te.account_id,
                            a.name AS account_name,
                            te.amount,
                            te.type,
                            te.currency,
                            te.to_amount,
                            te.to_currency,
                            te.instrument_id,
                            te.quantity,
                            i.symbol AS instrument_symbol,
                            te.envelope_id
                        FROM transactions t
                        LEFT JOIN transaction_entries te ON t.id = te.transaction_id
                        LEFT JOIN accounts a ON te.account_id = a.id
                        LEFT JOIN instruments i ON te.instrument_id = i.id
                        WHERE t.ledger_id = :ledgerId AND t.id = :transactionId
                        """)
                .param("ledgerId", ledgerId)
                .param("transactionId", transactionId)
                .query(DBTransactionWithEntry.class)
                .list();

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(groupToTransactions(results).getFirst());
    }

    @Override
    public Transaction create(String ledgerId, Transaction transaction) {
        jdbcClient.sql("""
                        INSERT INTO transactions (id, ledger_id, date, description, created_at)
                        VALUES (:id, :ledgerId, :date, :description, :createdAt)
                        """)
                .param("id", transaction.id())
                .param("ledgerId", ledgerId)
                .param("date", Date.valueOf(transaction.date()))
                .param("description", transaction.description())
                .param("createdAt", Timestamp.from(transaction.createdAt()))
                .update();

        insertEntries(transaction);

        return transaction;
    }

    @Override
    public Transaction update(String ledgerId, Transaction transaction) {
        // Update transaction
        jdbcClient.sql("""
                        UPDATE transactions SET date = :date, description = :description
                        WHERE id = :id AND ledger_id = :ledgerId
                        """)
                .param("id", transaction.id())
                .param("ledgerId", ledgerId)
                .param("date", java.sql.Date.valueOf(transaction.date()))
                .param("description", transaction.description())
                .update();

        // Delete existing entries
        jdbcClient.sql("DELETE FROM transaction_entries WHERE transaction_id = :transactionId")
                .param("transactionId", transaction.id())
                .update();

        insertEntries(transaction);

        return transaction;
    }


    private void insertEntries(Transaction transaction) {
        List<TransactionEntry> allEntries = new ArrayList<>();
        allEntries.addAll(transaction.debitEntries());
        allEntries.addAll(transaction.creditEntries());

        for (var entry : allEntries) {
            jdbcClient.sql("""
                            INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, to_amount, to_currency, instrument_id, quantity, envelope_id)
                            VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :toAmount, :toCurrency, :instrumentId, :quantity, :envelopeId)
                            """)
                    .param("id", UuidCreator.getTimeOrderedEpoch().toString())
                    .param("transactionId", transaction.id())
                    .param("accountId", entry.accountId())
                    .param("amount", entry.amount().amount())
                    .param("type", entry.type().name())
                    .param("currency", entry.amount().currency())
                    .param("toAmount", entry.convertedAmount() != null ? entry.convertedAmount().amount() : null)
                    .param("toCurrency", entry.convertedAmount() != null ? entry.convertedAmount().currency() : null)
                    .param("instrumentId", entry.instrumentId())
                    .param("quantity", entry.quantity())
                    .param("envelopeId", entry.envelopeId())
                    .update();
        }
    }

    @Override
    public void delete(String ledgerId, String transactionId) {
        // First delete entries
        jdbcClient.sql("DELETE FROM transaction_entries WHERE transaction_id = :transactionId")
                .param("transactionId", transactionId)
                .update();

        // Then delete transaction
        jdbcClient.sql("DELETE FROM transactions WHERE id = :id AND ledger_id = :ledgerId")
                .param("id", transactionId)
                .param("ledgerId", ledgerId)
                .update();
    }

    private List<Transaction> groupToTransactions(List<DBTransactionWithEntry> results) {
        // Use LinkedHashMap to preserve insertion order (which comes from the ORDER BY in SQL)
        return results.stream()
                .collect(groupingBy(
                        r -> new DBTransaction(r.transactionId(), null, r.date(), r.description(), r.createdAt()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(e -> {
                    var dbTransaction = e.getKey();
                    var entries = e.getValue();

                    List<TransactionEntry> debitEntries = entries.stream()
                            .filter(entry -> entry.entryId() != null && "DEBIT".equals(entry.type()))
                            .map(this::toTransactionEntry)
                            .toList();

                    List<TransactionEntry> creditEntries = entries.stream()
                            .filter(entry -> entry.entryId() != null && "CREDIT".equals(entry.type()))
                            .map(this::toTransactionEntry)
                            .toList();

                    return new Transaction(
                            dbTransaction.id(),
                            dbTransaction.date(),
                            dbTransaction.createdAt(),
                            dbTransaction.description(),
                            creditEntries,
                            debitEntries
                    );
                })
                .toList();
    }

    private TransactionEntry toTransactionEntry(DBTransactionWithEntry entry) {
        MonetaryAmount convertedAmount = null;
        if (entry.toAmount() != null && entry.toCurrency() != null) {
            convertedAmount = new MonetaryAmount(entry.toAmount(), entry.toCurrency());
        }

        return new TransactionEntry(
                entry.accountId(),
                TransactionEntryType.valueOf(entry.type()),
                new MonetaryAmount(entry.amount(), entry.currency()),
                convertedAmount,
                entry.accountName(),
                entry.instrumentId(),
                entry.quantity(),
                entry.instrumentSymbol(),
                entry.envelopeId()
        );
    }

    @Override
    public List<String> findDistinctDescriptions(String ledgerId) {
        return jdbcClient.sql("""
                        SELECT DISTINCT description
                        FROM transactions
                        WHERE ledger_id = :ledgerId
                        ORDER BY description
                        """)
                .param("ledgerId", ledgerId)
                .query(String.class)
                .list();
    }

    @Override
    public List<Transaction> findTransactionTemplates(String ledgerId) {
        // Get the most recent transaction for each unique description
        final var results = jdbcClient.sql("""
                        WITH latest_transactions AS (
                            SELECT DISTINCT ON (description)
                                id, ledger_id, date, description, created_at
                            FROM transactions
                            WHERE ledger_id = :ledgerId
                            ORDER BY description, date DESC, created_at DESC
                        )
                        SELECT
                            t.id AS transaction_id,
                            t.date,
                            t.description,
                            t.created_at,
                            te.id AS entry_id,
                            te.account_id,
                            a.name AS account_name,
                            te.amount,
                            te.type,
                            te.currency,
                            te.to_amount,
                            te.to_currency,
                            te.instrument_id,
                            te.quantity,
                            i.symbol AS instrument_symbol,
                            te.envelope_id
                        FROM latest_transactions t
                        LEFT JOIN transaction_entries te ON t.id = te.transaction_id
                        LEFT JOIN accounts a ON te.account_id = a.id
                        LEFT JOIN instruments i ON te.instrument_id = i.id
                        ORDER BY t.description
                        """)
                .param("ledgerId", ledgerId)
                .query(DBTransactionWithEntry.class)
                .list();

        return groupToTransactions(results);
    }

    record DBMonthlyReportRow(String currency, String category, BigDecimal total) {}

    @Override
    public MonthlyReport getMonthlyReport(String ledgerId, int year, int month) {
        final var rows = jdbcClient.sql("""
                        SELECT
                            COALESCE(te.to_currency, te.currency) AS currency,
                            CASE
                                WHEN a.type = 'INCOME' AND te.type = 'CREDIT' THEN 'INCOME'
                                WHEN a.type = 'EXPENSE' AND te.type = 'DEBIT' THEN 'EXPENSE'
                            END AS category,
                            SUM(COALESCE(te.to_amount, te.amount)) AS total
                        FROM transactions t
                        JOIN transaction_entries te ON t.id = te.transaction_id
                        JOIN accounts a ON te.account_id = a.id
                        WHERE t.ledger_id = :ledgerId
                          AND t.date >= :periodStart
                          AND t.date < :periodEnd
                          AND (
                              (a.type = 'INCOME' AND te.type = 'CREDIT')
                              OR (a.type = 'EXPENSE' AND te.type = 'DEBIT')
                          )
                        GROUP BY COALESCE(te.to_currency, te.currency),
                                 CASE
                                     WHEN a.type = 'INCOME' AND te.type = 'CREDIT' THEN 'INCOME'
                                     WHEN a.type = 'EXPENSE' AND te.type = 'DEBIT' THEN 'EXPENSE'
                                 END
                        """)
                .param("ledgerId", ledgerId)
                .param("periodStart", monthStart(year, month))
                .param("periodEnd", monthEnd(year, month))
                .query(DBMonthlyReportRow.class)
                .list();

        Map<String, BigDecimal> incomeByCurrency = new HashMap<>();
        Map<String, BigDecimal> expensesByCurrency = new HashMap<>();

        for (var row : rows) {
            if ("INCOME".equals(row.category())) {
                incomeByCurrency.put(row.currency(), row.total());
            } else if ("EXPENSE".equals(row.category())) {
                expensesByCurrency.put(row.currency(), row.total());
            }
        }

        return new MonthlyReport(year, month, incomeByCurrency, expensesByCurrency);
    }

    record DBExpenseBreakdownRow(String accountId, BigDecimal amount) {}

    @Override
    public MonthlyExpenseBreakdown getMonthlyExpenseBreakdown(String ledgerId, int year, int month) {
        final var rows = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            SELECT id, id AS root_id
                            FROM accounts
                            WHERE ledger_id = :ledgerId

                            UNION ALL

                            SELECT a.id, at.root_id
                            FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
                        )
                        SELECT
                            a.id AS account_id,
                            SUM(ms.debit_total) - SUM(ms.credit_total) AS amount
                        FROM accounts a
                        INNER JOIN account_tree at ON at.root_id = a.id
                        INNER JOIN monthly_account_summary ms
                            ON ms.account_id = at.id
                           AND ms.currency = a.currency
                           AND ms.period_year = :year
                           AND ms.period_month = :month
                        WHERE a.ledger_id = :ledgerId
                          AND a.type = 'EXPENSE'
                        GROUP BY a.id
                        """)
                .param("ledgerId", ledgerId)
                .param("year", year)
                .param("month", month)
                .query(DBExpenseBreakdownRow.class)
                .list();

        Map<String, BigDecimal> expensesByAccountId = new HashMap<>();
        for (var row : rows) {
            if (row.amount() != null && row.amount().compareTo(BigDecimal.ZERO) != 0) {
                expensesByAccountId.put(row.accountId(), row.amount());
            }
        }

        return new MonthlyExpenseBreakdown(year, month, expensesByAccountId);
    }

    record DBIncomeBreakdownRow(String accountId, BigDecimal amount) {}

    @Override
    public MonthlyIncomeBreakdown getMonthlyIncomeBreakdown(String ledgerId, int year, int month) {
        final var rows = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            SELECT id, id AS root_id
                            FROM accounts
                            WHERE ledger_id = :ledgerId

                            UNION ALL

                            SELECT a.id, at.root_id
                            FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
                        )
                        SELECT
                            a.id AS account_id,
                            SUM(ms.credit_total) - SUM(ms.debit_total) AS amount
                        FROM accounts a
                        INNER JOIN account_tree at ON at.root_id = a.id
                        INNER JOIN monthly_account_summary ms
                            ON ms.account_id = at.id
                           AND ms.currency = a.currency
                           AND ms.period_year = :year
                           AND ms.period_month = :month
                        WHERE a.ledger_id = :ledgerId
                          AND a.type = 'INCOME'
                        GROUP BY a.id
                        """)
                .param("ledgerId", ledgerId)
                .param("year", year)
                .param("month", month)
                .query(DBIncomeBreakdownRow.class)
                .list();

        Map<String, BigDecimal> incomeByAccountId = new HashMap<>();
        for (var row : rows) {
            if (row.amount() != null && row.amount().compareTo(BigDecimal.ZERO) != 0) {
                incomeByAccountId.put(row.accountId(), row.amount());
            }
        }

        return new MonthlyIncomeBreakdown(year, month, incomeByAccountId);
    }
}
