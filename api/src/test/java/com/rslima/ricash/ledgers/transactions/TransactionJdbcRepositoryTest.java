package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.MonetaryAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the multi-statement write paths (create = 1+N inserts,
 * update = delete-then-reinsert, delete = 2 statements) and asserts the
 * trigger-maintained summary tables after each write. Guards the
 * insertEntries extraction and any future SQL refactoring.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class TransactionJdbcRepositoryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private TransactionJdbcRepository repository;

    private static final String LEDGER_ID = "ledger-1";
    private static final String CHECKING = "account-checking";
    private static final String GROCERIES = "account-groceries";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    @BeforeEach
    void setUp() {
        repository = new TransactionJdbcRepository(jdbcClient);

        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();

        jdbcClient.sql("INSERT INTO users (id) VALUES ('user-1')").update();
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, 'user-1', 'ledger-1', 'Ledger', NULL, 'USD', :createdAt)
                        """)
                .param("id", LEDGER_ID)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
        insertAccount(CHECKING, "Checking", "ASSET", "USD");
        insertAccount(GROCERIES, "Groceries", "EXPENSE", "BRL");
    }

    @Test
    void create_withMixedAndConvertedEntries_readsBack() {
        var transaction = newTransaction("tx-1", "Groceries abroad",
                List.of(credit(CHECKING, "20.00", "USD")),
                List.of(debitConverted(GROCERIES, "20.00", "USD", "100.00", "BRL")));

        repository.create(LEDGER_ID, transaction);

        var loaded = repository.findById(LEDGER_ID, "tx-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().description()).isEqualTo("Groceries abroad");
        assertThat(loaded.get().creditEntries()).hasSize(1);
        assertThat(loaded.get().debitEntries()).hasSize(1);

        var debit = loaded.get().debitEntries().getFirst();
        assertThat(debit.accountId()).isEqualTo(GROCERIES);
        assertThat(debit.accountName()).isEqualTo("Groceries");
        assertThat(debit.amount().amount()).isEqualByComparingTo("20.00");
        assertThat(debit.amount().currency()).isEqualTo("USD");
        assertThat(debit.convertedAmount().amount()).isEqualByComparingTo("100.00");
        assertThat(debit.convertedAmount().currency()).isEqualTo("BRL");

        // Summary triggers credit the account currency: the converted amount
        // lands in the BRL bucket for the expense account.
        assertThat(summaryDebit(GROCERIES, "BRL")).isEqualByComparingTo("100.00");
        assertThat(summaryCredit(CHECKING, "USD")).isEqualByComparingTo("20.00");
    }

    @Test
    void update_replacesEntriesAndSummaries() {
        repository.create(LEDGER_ID, newTransaction("tx-1", "Original",
                List.of(credit(CHECKING, "20.00", "USD")),
                List.of(debitConverted(GROCERIES, "20.00", "USD", "100.00", "BRL"))));

        repository.update(LEDGER_ID, newTransaction("tx-1", "Edited",
                List.of(credit(CHECKING, "30.00", "USD")),
                List.of(debitConverted(GROCERIES, "30.00", "USD", "150.00", "BRL"))));

        var loaded = repository.findById(LEDGER_ID, "tx-1");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().description()).isEqualTo("Edited");
        assertThat(countEntries("tx-1")).isEqualTo(2);
        assertThat(summaryDebit(GROCERIES, "BRL")).isEqualByComparingTo("150.00");
        assertThat(summaryCredit(CHECKING, "USD")).isEqualByComparingTo("30.00");
    }

    @Test
    void delete_removesEntriesAndResetsSummaries() {
        repository.create(LEDGER_ID, newTransaction("tx-1", "Doomed",
                List.of(credit(CHECKING, "20.00", "USD")),
                List.of(debitConverted(GROCERIES, "20.00", "USD", "100.00", "BRL"))));

        repository.delete(LEDGER_ID, "tx-1");

        assertThat(repository.findById(LEDGER_ID, "tx-1")).isEmpty();
        assertThat(countEntries("tx-1")).isZero();
        assertThat(summaryDebit(GROCERIES, "BRL")).isEqualByComparingTo("0.00");
        assertThat(summaryCredit(CHECKING, "USD")).isEqualByComparingTo("0.00");
    }

    @Test
    void findById_wrongLedger_returnsEmpty() {
        repository.create(LEDGER_ID, newTransaction("tx-1", "Mine",
                List.of(credit(CHECKING, "20.00", "USD")),
                List.of(debitConverted(GROCERIES, "20.00", "USD", "100.00", "BRL"))));

        assertThat(repository.findById("other-ledger", "tx-1")).isEmpty();
    }

    private static Transaction newTransaction(String id, String description,
                                              List<TransactionEntry> credits, List<TransactionEntry> debits) {
        return new Transaction(id, DATE, Instant.now(), description, credits, debits);
    }

    private static TransactionEntry credit(String accountId, String amount, String currency) {
        return new TransactionEntry(accountId, TransactionEntryType.CREDIT,
                new MonetaryAmount(new BigDecimal(amount), currency), null, null);
    }

    private static TransactionEntry debitConverted(String accountId, String amount, String currency,
                                                   String toAmount, String toCurrency) {
        return new TransactionEntry(accountId, TransactionEntryType.DEBIT,
                new MonetaryAmount(new BigDecimal(amount), currency),
                new MonetaryAmount(new BigDecimal(toAmount), toCurrency), null);
    }

    private long countEntries(String transactionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM transaction_entries WHERE transaction_id = :id")
                .param("id", transactionId)
                .query(Long.class)
                .single();
    }

    private BigDecimal summaryDebit(String accountId, String currency) {
        return summaryValue(accountId, currency, "debit_total");
    }

    private BigDecimal summaryCredit(String accountId, String currency) {
        return summaryValue(accountId, currency, "credit_total");
    }

    private BigDecimal summaryValue(String accountId, String currency, String column) {
        return jdbcClient.sql("SELECT COALESCE(SUM(" + column + "), 0) FROM account_balance_summary " +
                        "WHERE account_id = :accountId AND currency = :currency")
                .param("accountId", accountId)
                .param("currency", currency)
                .query(BigDecimal.class)
                .single();
    }

    private void insertAccount(String id, String name, String type, String currency) {
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, NULL, :slug, :name, NULL, :currency, :type, 'ACTIVE', :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", LEDGER_ID)
                .param("slug", name.toLowerCase())
                .param("name", name)
                .param("currency", currency)
                .param("type", type)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }
}
