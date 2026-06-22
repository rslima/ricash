package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the trigger-maintained monthly_account_summary and
 * envelope_monthly_summary tables (V13): expense/income breakdowns roll up the
 * account subtree currency-aware, envelope spend uses the effective amount, and
 * both stay fresh when a transaction's month changes or entries are deleted.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class MonthlySummaryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private TransactionJdbcRepository transactionRepository;
    private EnvelopeAllocationJdbcRepository envelopeAllocationRepository;

    @BeforeEach
    void setUp() {
        transactionRepository = new TransactionJdbcRepository(jdbcClient);
        envelopeAllocationRepository = new EnvelopeAllocationJdbcRepository(jdbcClient);

        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM envelopes").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();

        jdbcClient.sql("INSERT INTO users (id) VALUES ('user-1')").update();
        insertLedger("ledger-1", "user-1", "Main", "USD");

        insertAccount("checking", "ledger-1", null, "Checking", "USD", "ASSET");
        insertAccount("income", "ledger-1", null, "Income", "USD", "INCOME");
        insertAccount("expenses", "ledger-1", null, "Expenses", "USD", "EXPENSE");
        insertAccount("groceries", "ledger-1", "expenses", "Groceries", "USD", "EXPENSE");
        insertEnvelope("food", "ledger-1", "Food", "USD");

        // T1 (Jan 5): salary -> income +1000
        insertTransaction("t1", "ledger-1", "Salary", LocalDate.of(2026, 1, 5));
        insertEntry("t1", "checking", "1000.00", "DEBIT", "USD", null, null, null);
        insertEntry("t1", "income", "1000.00", "CREDIT", "USD", null, null, null);

        // T2 (Jan 10): groceries 200 USD, tagged to Food envelope
        insertTransaction("t2", "ledger-1", "Groceries", LocalDate.of(2026, 1, 10));
        insertEntry("t2", "groceries", "200.00", "DEBIT", "USD", null, null, "food");
        insertEntry("t2", "checking", "200.00", "CREDIT", "USD", null, null, null);

        // T3 (Jan 20): groceries paid in EUR, converted to 55 USD (account currency)
        insertTransaction("t3", "ledger-1", "EUR snack", LocalDate.of(2026, 1, 20));
        insertEntry("t3", "groceries", "50.00", "DEBIT", "EUR", "55.00", "USD", "food");
        insertEntry("t3", "checking", "55.00", "CREDIT", "USD", null, null, null);
    }

    @Test
    void expenseBreakdown_rollsUpSubtreeCurrencyAware() {
        var breakdown = transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 1);

        // groceries: 200 (T2) + 55 (T3 converted to USD) = 255; EUR facet excluded by currency match
        assertThat(breakdown.expensesByAccountId().get("groceries")).isEqualByComparingTo("255.00");
        // expenses parent rolls up groceries
        assertThat(breakdown.expensesByAccountId().get("expenses")).isEqualByComparingTo("255.00");
    }

    @Test
    void incomeBreakdown_sumsCreditsForIncomeAccounts() {
        var breakdown = transactionRepository.getMonthlyIncomeBreakdown("ledger-1", 2026, 1);
        assertThat(breakdown.incomeByAccountId().get("income")).isEqualByComparingTo("1000.00");
    }

    @Test
    void envelopeSpent_usesEffectiveAmountAcrossEntries() {
        // 200 (T2) + 55 (T3 effective to_amount) = 255
        assertThat(envelopeAllocationRepository.calculateSpentForEnvelope("food", 2026, 1))
                .isEqualByComparingTo("255.00");
    }

    @Test
    void breakdownExcludesOtherMonths() {
        assertThat(transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 2)
                .expensesByAccountId()).isEmpty();
        assertThat(envelopeAllocationRepository.calculateSpentForEnvelope("food", 2026, 2))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void changingTransactionMonth_reBucketsSummaries() {
        // Move T2 (groceries 200) from January to February.
        jdbcClient.sql("UPDATE transactions SET date = :date WHERE id = 't2'")
                .param("date", Date.valueOf(LocalDate.of(2026, 2, 10)))
                .update();

        // January now only has T3's 55; February has T2's 200.
        assertThat(transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 1)
                .expensesByAccountId().get("groceries")).isEqualByComparingTo("55.00");
        assertThat(transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 2)
                .expensesByAccountId().get("groceries")).isEqualByComparingTo("200.00");

        assertThat(envelopeAllocationRepository.calculateSpentForEnvelope("food", 2026, 1))
                .isEqualByComparingTo("55.00");
        assertThat(envelopeAllocationRepository.calculateSpentForEnvelope("food", 2026, 2))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void deletingTransaction_immediatelyUpdatesSummaries() {
        jdbcClient.sql("DELETE FROM transaction_entries WHERE transaction_id = 't3'").update();
        jdbcClient.sql("DELETE FROM transactions WHERE id = 't3'").update();

        assertThat(transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 1)
                .expensesByAccountId().get("groceries")).isEqualByComparingTo("200.00");
        assertThat(envelopeAllocationRepository.calculateSpentForEnvelope("food", 2026, 1))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void summaryTablesMatchIndependentRecomputation() {
        var accountMismatches = jdbcClient.sql("""
                        WITH expected AS (
                            SELECT account_id, period_year, period_month, currency,
                                   SUM(CASE WHEN type = 'DEBIT'  THEN amt ELSE 0 END) AS debit_total,
                                   SUM(CASE WHEN type = 'CREDIT' THEN amt ELSE 0 END) AS credit_total
                            FROM (
                                SELECT te.account_id,
                                       EXTRACT(YEAR FROM t.date)::int AS period_year,
                                       EXTRACT(MONTH FROM t.date)::int AS period_month,
                                       te.currency, te.type, te.amount AS amt
                                FROM transaction_entries te JOIN transactions t ON t.id = te.transaction_id
                                UNION ALL
                                SELECT te.account_id,
                                       EXTRACT(YEAR FROM t.date)::int,
                                       EXTRACT(MONTH FROM t.date)::int,
                                       te.to_currency, te.type, te.to_amount
                                FROM transaction_entries te JOIN transactions t ON t.id = te.transaction_id
                                WHERE te.to_currency IS NOT NULL AND te.to_amount IS NOT NULL
                            ) f
                            GROUP BY account_id, period_year, period_month, currency
                        )
                        SELECT COUNT(*) FROM expected e
                        FULL OUTER JOIN monthly_account_summary s
                          ON s.account_id = e.account_id AND s.period_year = e.period_year
                         AND s.period_month = e.period_month AND s.currency = e.currency
                        WHERE COALESCE(e.debit_total, 0)  <> COALESCE(s.debit_total, 0)
                           OR COALESCE(e.credit_total, 0) <> COALESCE(s.credit_total, 0)
                        """)
                .query(Long.class)
                .single();
        assertThat(accountMismatches).isZero();

        var envelopeMismatches = jdbcClient.sql("""
                        WITH expected AS (
                            SELECT te.envelope_id,
                                   EXTRACT(YEAR FROM t.date)::int AS period_year,
                                   EXTRACT(MONTH FROM t.date)::int AS period_month,
                                   COALESCE(te.to_currency, te.currency) AS currency,
                                   SUM(COALESCE(te.to_amount, te.amount)) AS spent_total
                            FROM transaction_entries te JOIN transactions t ON t.id = te.transaction_id
                            WHERE te.envelope_id IS NOT NULL AND te.type = 'DEBIT'
                            GROUP BY te.envelope_id, EXTRACT(YEAR FROM t.date)::int,
                                     EXTRACT(MONTH FROM t.date)::int, COALESCE(te.to_currency, te.currency)
                        )
                        SELECT COUNT(*) FROM expected e
                        FULL OUTER JOIN envelope_monthly_summary s
                          ON s.envelope_id = e.envelope_id AND s.period_year = e.period_year
                         AND s.period_month = e.period_month AND s.currency = e.currency
                        WHERE COALESCE(e.spent_total, 0) <> COALESCE(s.spent_total, 0)
                        """)
                .query(Long.class)
                .single();
        assertThat(envelopeMismatches).isZero();
    }

    private void insertLedger(String id, String userId, String name, String currency) {
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, :name, null, :currency, :createdAt)
                        """)
                .param("id", id).param("userId", userId).param("slug", name.toLowerCase())
                .param("name", name).param("currency", currency)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertAccount(String id, String ledgerId, String parentId, String name, String currency, String type) {
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentId, :slug, :name, null, :currency, :type, 'ACTIVE', :createdAt)
                        """)
                .param("id", id).param("ledgerId", ledgerId).param("parentId", parentId)
                .param("slug", id).param("name", name).param("currency", currency).param("type", type)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertEnvelope(String id, String ledgerId, String name, String currency) {
        jdbcClient.sql("""
                        INSERT INTO envelopes (id, ledger_id, parent_envelope_id, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, null, :name, null, :currency, 'EXPENSE', 'ACTIVE', :createdAt)
                        """)
                .param("id", id).param("ledgerId", ledgerId).param("name", name).param("currency", currency)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertTransaction(String id, String ledgerId, String description, LocalDate date) {
        jdbcClient.sql("""
                        INSERT INTO transactions (id, ledger_id, description, date, created_at)
                        VALUES (:id, :ledgerId, :description, :date, :createdAt)
                        """)
                .param("id", id).param("ledgerId", ledgerId).param("description", description)
                .param("date", Date.valueOf(date)).param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertEntry(String transactionId, String accountId, String amount, String type,
                             String currency, String toAmount, String toCurrency, String envelopeId) {
        jdbcClient.sql("""
                        INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, to_amount, to_currency, envelope_id)
                        VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :toAmount, :toCurrency, :envelopeId)
                        """)
                .param("id", java.util.UUID.randomUUID().toString())
                .param("transactionId", transactionId).param("accountId", accountId)
                .param("amount", new BigDecimal(amount)).param("type", type).param("currency", currency)
                .param("toAmount", toAmount != null ? new BigDecimal(toAmount) : null)
                .param("toCurrency", toCurrency).param("envelopeId", envelopeId)
                .update();
    }
}
