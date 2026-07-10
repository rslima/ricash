package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the double-entry write path through the Spring-proxied
 * TransactionService so transaction boundaries (or their absence) are
 * observable. Deliberately NOT @Transactional: a test-managed transaction
 * would swallow the very rollback behavior under test.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
class TransactionJdbcRepositoryTest {

    private static final String USER = "user-1";
    private static final String LEDGER_SLUG = "main";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionService transactionService;

    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser(USER);
        fixtures.insertLedger("ledger-1", USER, "Main", "USD");
        fixtures.insertAccount("checking", "ledger-1", null, "Checking", "USD", "ASSET");
        fixtures.insertAccount("income", "ledger-1", null, "Income", "USD", "INCOME");
        fixtures.insertEnvelope("food", "ledger-1", "Food", "USD");
    }

    @Test
    void create_persistsTransactionWithEntries() {
        var created = transactionService.create(USER, LEDGER_SLUG, createRequest("Salary", "100.00", null));

        var found = transactionService.find(USER, LEDGER_SLUG, created.id()).orElseThrow();
        assertThat(found.description()).isEqualTo("Salary");
        assertThat(found.debitEntries()).hasSize(1);
        assertThat(found.creditEntries()).hasSize(1);
        assertThat(checkingDebitTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void update_replacesEntries() {
        var created = transactionService.create(USER, LEDGER_SLUG, createRequest("Salary", "100.00", null));

        transactionService.update(USER, LEDGER_SLUG, created.id(), updateRequest("Bonus", "250.00", null));

        var found = transactionService.find(USER, LEDGER_SLUG, created.id()).orElseThrow();
        assertThat(found.description()).isEqualTo("Bonus");
        assertThat(checkingDebitTotal()).isEqualByComparingTo("250.00");
    }

    @Test
    void delete_removesTransactionAndEntries() {
        var created = transactionService.create(USER, LEDGER_SLUG, createRequest("Salary", "100.00", null));

        transactionService.delete(USER, LEDGER_SLUG, created.id());

        assertThat(transactionService.find(USER, LEDGER_SLUG, created.id())).isEmpty();
        assertThat(entryCount()).isZero();
        assertThat(checkingDebitTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void create_failingEntryInsert_leavesNoOrphanTransaction() {
        // The ghost envelope passes service validation (envelopes are not checked
        // there) but violates the FK on the entry INSERT, after the header INSERT.
        assertThatThrownBy(() ->
                transactionService.create(USER, LEDGER_SLUG, createRequest("Broken", "100.00", "ghost-envelope")))
                .isInstanceOf(DataAccessException.class);

        assertThat(transactionCount()).as("orphan transaction header must be rolled back").isZero();
        assertThat(entryCount()).isZero();
        assertThat(checkingDebitTotal()).isEqualByComparingTo("0.00");
    }

    @Test
    void update_failingEntryInsert_preservesOriginalEntriesAndBalances() {
        var created = transactionService.create(USER, LEDGER_SLUG, createRequest("Salary", "100.00", null));

        // update = UPDATE header + DELETE all entries + re-INSERT; the ghost
        // envelope makes the re-INSERT fail after the delete already ran.
        assertThatThrownBy(() ->
                transactionService.update(USER, LEDGER_SLUG, created.id(), updateRequest("Broken", "250.00", "ghost-envelope")))
                .isInstanceOf(DataAccessException.class);

        var found = transactionService.find(USER, LEDGER_SLUG, created.id()).orElseThrow();
        assertThat(found.description()).as("header update must be rolled back").isEqualTo("Salary");
        assertThat(found.debitEntries()).as("original entries must survive the failed update").hasSize(1);
        assertThat(found.creditEntries()).hasSize(1);
        assertThat(found.debitEntries().getFirst().amount().amount()).isEqualByComparingTo("100.00");
        assertThat(checkingDebitTotal())
                .as("trigger-maintained balance summary must be unchanged")
                .isEqualByComparingTo("100.00");
    }

    private CreateTransactionRequest createRequest(String description, String amount, String envelopeId) {
        return new CreateTransactionRequest(LocalDate.of(2026, 1, 10), description, List.of(
                new TransactionEntryRequest(
                        "checking", new BigDecimal(amount), "USD", null, null,
                        TransactionEntryType.DEBIT, null, null, envelopeId),
                new TransactionEntryRequest(
                        "income", new BigDecimal(amount), "USD", null, null,
                        TransactionEntryType.CREDIT, null, null, null)));
    }

    private UpdateTransactionRequest updateRequest(String description, String amount, String envelopeId) {
        return new UpdateTransactionRequest(LocalDate.of(2026, 1, 12), description, List.of(
                new TransactionEntryRequest(
                        "checking", new BigDecimal(amount), "USD", null, null,
                        TransactionEntryType.DEBIT, null, null, envelopeId),
                new TransactionEntryRequest(
                        "income", new BigDecimal(amount), "USD", null, null,
                        TransactionEntryType.CREDIT, null, null, null)));
    }

    private long transactionCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM transactions").query(Long.class).single();
    }

    private long entryCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM transaction_entries").query(Long.class).single();
    }

    private BigDecimal checkingDebitTotal() {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(debit_total), 0) FROM account_balance_summary
                        WHERE account_id = 'checking' AND currency = 'USD'
                        """)
                .query(BigDecimal.class)
                .single();
    }
}
