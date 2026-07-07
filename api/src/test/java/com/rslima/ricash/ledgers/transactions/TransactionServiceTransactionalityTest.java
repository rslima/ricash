package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves that multi-statement writes are atomic: a failure on the Nth statement
 * must leave earlier statements of the same operation rolled back. The update
 * path is delete-all-entries + re-insert, so a mid-insert failure without a
 * transaction would silently destroy the original entries (and the
 * trigger-maintained summary tables with them).
 *
 * Not annotated with @Transactional on purpose — a test-managed transaction
 * would mask the service's own transaction boundary.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
class TransactionServiceTransactionalityTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcClient jdbcClient;

    private static final String USER_ID = "tx-user";
    private static final String LEDGER_ID = "tx-ledger";
    private static final String LEDGER_SLUG = "tx-ledger";
    private static final String CHECKING = "tx-account-checking";
    private static final String GROCERIES = "tx-account-groceries";

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM instrument_prices").update();
        jdbcClient.sql("DELETE FROM instruments").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();

        jdbcClient.sql("INSERT INTO users (id) VALUES (:id)").param("id", USER_ID).update();
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, 'Tx Ledger', NULL, 'USD', :createdAt)
                        """)
                .param("id", LEDGER_ID)
                .param("userId", USER_ID)
                .param("slug", LEDGER_SLUG)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
        insertAccount(CHECKING, "Checking", "ASSET");
        insertAccount(GROCERIES, "Groceries", "EXPENSE");
    }

    @Test
    void transactionServiceIsProxied() {
        assertThat(AopUtils.isAopProxy(transactionService))
                .as("@Transactional requires the service bean to be proxied")
                .isTrue();
    }

    @Test
    void update_failingMidWrite_keepsOriginalEntriesAndSummaries() {
        var created = transactionService.create(USER_ID, LEDGER_SLUG, new CreateTransactionRequest(
                LocalDate.of(2026, 1, 15),
                "Groceries run",
                List.of(
                        new CreateTransactionRequest.EntryRequest(GROCERIES, new BigDecimal("100.00"), "USD", null, null, TransactionEntryType.DEBIT, null, null, null),
                        new CreateTransactionRequest.EntryRequest(CHECKING, new BigDecimal("100.00"), "USD", null, null, TransactionEntryType.CREDIT, null, null, null)
                )));

        var entriesBefore = countEntries(created.id());
        var summariesBefore = snapshotSummaries();
        assertThat(entriesBefore).isEqualTo(2);

        // Second entry references a nonexistent instrument: the FK violation fires
        // on the second INSERT, after the original entries were already deleted
        // and the first new entry inserted.
        var badUpdate = new UpdateTransactionRequest(
                LocalDate.of(2026, 1, 16),
                "Groceries run (edited)",
                List.of(
                        new UpdateTransactionRequest.EntryRequest(GROCERIES, new BigDecimal("50.00"), "USD", null, null, TransactionEntryType.DEBIT, null, null, null),
                        new UpdateTransactionRequest.EntryRequest(CHECKING, new BigDecimal("50.00"), "USD", null, null, TransactionEntryType.CREDIT, "no-such-instrument", BigDecimal.ONE, null)
                ));

        assertThatThrownBy(() -> transactionService.update(USER_ID, LEDGER_SLUG, created.id(), badUpdate))
                .isInstanceOf(Exception.class);

        assertThat(countEntries(created.id()))
                .as("original entries must survive a failed update")
                .isEqualTo(entriesBefore);
        assertThat(descriptionOf(created.id()))
                .as("transaction row update must be rolled back")
                .isEqualTo("Groceries run");
        assertThat(snapshotSummaries())
                .as("trigger-maintained summary tables must be unchanged")
                .isEqualTo(summariesBefore);
    }

    @Test
    void create_failingMidWrite_leavesNothingBehind() {
        var badCreate = new CreateTransactionRequest(
                LocalDate.of(2026, 2, 1),
                "Poisoned create",
                List.of(
                        new CreateTransactionRequest.EntryRequest(GROCERIES, new BigDecimal("10.00"), "USD", null, null, TransactionEntryType.DEBIT, null, null, null),
                        new CreateTransactionRequest.EntryRequest(CHECKING, new BigDecimal("10.00"), "USD", null, null, TransactionEntryType.CREDIT, "no-such-instrument", BigDecimal.ONE, null)
                ));

        assertThatThrownBy(() -> transactionService.create(USER_ID, LEDGER_SLUG, badCreate))
                .isInstanceOf(Exception.class);

        var transactions = jdbcClient.sql("SELECT COUNT(*) FROM transactions WHERE ledger_id = :ledgerId")
                .param("ledgerId", LEDGER_ID)
                .query(Long.class)
                .single();
        assertThat(transactions).as("failed create must not leave an orphan transaction row").isZero();
    }

    private long countEntries(String transactionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM transaction_entries WHERE transaction_id = :id")
                .param("id", transactionId)
                .query(Long.class)
                .single();
    }

    private String descriptionOf(String transactionId) {
        return jdbcClient.sql("SELECT description FROM transactions WHERE id = :id")
                .param("id", transactionId)
                .query(String.class)
                .single();
    }

    private List<String> snapshotSummaries() {
        var balances = jdbcClient.sql("""
                        SELECT account_id || '|' || currency || '|' || debit_total || '|' || credit_total
                        FROM account_balance_summary ORDER BY account_id, currency
                        """)
                .query(String.class)
                .list();
        var monthly = jdbcClient.sql("""
                        SELECT account_id || '|' || period_year || '|' || period_month || '|' || debit_total || '|' || credit_total
                        FROM monthly_account_summary ORDER BY account_id, period_year, period_month
                        """)
                .query(String.class)
                .list();
        return List.of(String.join(";", balances), String.join(";", monthly));
    }

    private void insertAccount(String id, String name, String type) {
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, NULL, :slug, :name, NULL, 'USD', :type, 'ACTIVE', :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", LEDGER_ID)
                .param("slug", name.toLowerCase())
                .param("name", name)
                .param("type", type)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }
}
