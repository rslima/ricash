package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationJdbcRepository;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
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
    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        transactionRepository = new TransactionJdbcRepository(jdbcClient);
        envelopeAllocationRepository = new EnvelopeAllocationJdbcRepository(jdbcClient);
        fixtures = new DbFixtures(jdbcClient);

        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertLedger("ledger-1", "user-1", "Main", "USD");

        fixtures.insertAccount("checking", "ledger-1", null, "Checking", "USD", "ASSET");
        fixtures.insertAccount("income", "ledger-1", null, "Income", "USD", "INCOME");
        fixtures.insertAccount("expenses", "ledger-1", null, "Expenses", "USD", "EXPENSE");
        fixtures.insertAccount("groceries", "ledger-1", "expenses", "Groceries", "USD", "EXPENSE");
        fixtures.insertEnvelope("food", "ledger-1", "Food", "USD");

        // T1 (Jan 5): salary -> income +1000
        fixtures.insertTransaction("t1", "ledger-1", "Salary", LocalDate.of(2026, 1, 5));
        fixtures.insertEntry("t1", "checking", "1000.00", "DEBIT", "USD", null, null, null);
        fixtures.insertEntry("t1", "income", "1000.00", "CREDIT", "USD", null, null, null);

        // T2 (Jan 10): groceries 200 USD, tagged to Food envelope
        fixtures.insertTransaction("t2", "ledger-1", "Groceries", LocalDate.of(2026, 1, 10));
        fixtures.insertEntry("t2", "groceries", "200.00", "DEBIT", "USD", null, null, "food");
        fixtures.insertEntry("t2", "checking", "200.00", "CREDIT", "USD", null, null, null);

        // T3 (Jan 20): groceries paid in EUR, converted to 55 USD (account currency)
        fixtures.insertTransaction("t3", "ledger-1", "EUR snack", LocalDate.of(2026, 1, 20));
        fixtures.insertEntry("t3", "groceries", "50.00", "DEBIT", "EUR", "55.00", "USD", "food");
        fixtures.insertEntry("t3", "checking", "55.00", "CREDIT", "USD", null, null, null);
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
        assertThat(spentFor("food", 2026, 1))
                .isEqualByComparingTo("255.00");
    }

    @Test
    void breakdownExcludesOtherMonths() {
        assertThat(transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 2)
                .expensesByAccountId()).isEmpty();
        assertThat(spentFor("food", 2026, 2))
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

        assertThat(spentFor("food", 2026, 1))
                .isEqualByComparingTo("55.00");
        assertThat(spentFor("food", 2026, 2))
                .isEqualByComparingTo("200.00");
    }

    @Test
    void deletingTransaction_immediatelyUpdatesSummaries() {
        jdbcClient.sql("DELETE FROM transaction_entries WHERE transaction_id = 't3'").update();
        jdbcClient.sql("DELETE FROM transactions WHERE id = 't3'").update();

        assertThat(transactionRepository.getMonthlyExpenseBreakdown("ledger-1", 2026, 1)
                .expensesByAccountId().get("groceries")).isEqualByComparingTo("200.00");
        assertThat(spentFor("food", 2026, 1))
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

    /** Spent total for one envelope-month, read through the activity query. */
    private BigDecimal spentFor(String envelopeId, int year, int month) {
        return envelopeAllocationRepository.findMonthlyActivityByEnvelope(envelopeId, year, month).stream()
                .filter(activity -> activity.periodYear() == year && activity.periodMonth() == month)
                .map(com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationRepository.MonthlyActivity::spent)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
