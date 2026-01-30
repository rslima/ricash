package com.rslima.ricash.ledgers;

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
import java.util.List;
import java.util.Optional;

import java.util.LinkedHashMap;

import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
@Slf4j
public class TransactionJdbcRepository implements TransactionRepository {
    private final JdbcClient jdbcClient;

    record DBTransaction(String id, String ledgerId, LocalDate date, String description, Instant createdAt) {}

    record DBTransactionEntry(String id, String transactionId, String accountId, String accountName,
                               BigDecimal amount, String type, String currency,
                               BigDecimal toAmount, String toCurrency,
                               String instrumentId, BigDecimal quantity, String instrumentSymbol) {}

    record DBTransactionWithEntry(String transactionId, LocalDate date, String description, Instant createdAt,
                                   String entryId, String accountId, String accountName,
                                   BigDecimal amount, String type, String currency,
                                   BigDecimal toAmount, String toCurrency,
                                   String instrumentId, BigDecimal quantity, String instrumentSymbol) {}

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
                            i.symbol AS instrument_symbol
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
                            i.symbol AS instrument_symbol
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
                            i.symbol AS instrument_symbol
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

        // Insert all entries
        List<TransactionEntry> allEntries = new ArrayList<>();
        allEntries.addAll(transaction.debitEntries());
        allEntries.addAll(transaction.creditEntries());

        for (var entry : allEntries) {
            jdbcClient.sql("""
                            INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, to_amount, to_currency, instrument_id, quantity)
                            VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :toAmount, :toCurrency, :instrumentId, :quantity)
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
                    .update();
        }

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

        // Insert new entries
        List<TransactionEntry> allEntries = new ArrayList<>();
        allEntries.addAll(transaction.debitEntries());
        allEntries.addAll(transaction.creditEntries());

        for (var entry : allEntries) {
            jdbcClient.sql("""
                            INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, to_amount, to_currency, instrument_id, quantity)
                            VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :toAmount, :toCurrency, :instrumentId, :quantity)
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
                    .update();
        }

        return transaction;
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
                entry.instrumentSymbol()
        );
    }
}
