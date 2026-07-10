package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.LedgerJdbcRepository;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the trigger-maintained account_balance_summary table (V12) yields
 * the same balances the recursive-CTE-over-transaction_entries queries produced,
 * across parent/child rollups, multi-currency conversions, and that values stay
 * fresh after edits and deletes.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class AccountBalanceSummaryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private AccountJdbcRepository accountRepository;
    private LedgerJdbcRepository ledgerRepository;
    private DbFixtures fixtures;

    @BeforeEach
    void setUp() {
        accountRepository = new AccountJdbcRepository(jdbcClient);
        ledgerRepository = new LedgerJdbcRepository(jdbcClient);
        fixtures = new DbFixtures(jdbcClient);

        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertLedger("ledger-1", "user-1", "Main", "USD");

        // assets (parent) -> checking (child); plus income, expenses, and a EUR brokerage.
        fixtures.insertAccount("assets", "ledger-1", null, "Assets", "USD", "ASSET");
        fixtures.insertAccount("checking", "ledger-1", "assets", "Checking", "USD", "ASSET");
        fixtures.insertAccount("income", "ledger-1", null, "Income", "USD", "INCOME");
        fixtures.insertAccount("expenses", "ledger-1", null, "Expenses", "USD", "EXPENSE");
        fixtures.insertAccount("brokerage", "ledger-1", null, "Brokerage", "EUR", "ASSET");

        // T1 salary: checking +1000, income +1000
        fixtures.insertTransaction("t1", "ledger-1", "Salary", LocalDate.of(2026, 1, 5));
        fixtures.insertEntry("t1", "checking", "1000.00", "DEBIT", "USD", null, null);
        fixtures.insertEntry("t1", "income", "1000.00", "CREDIT", "USD", null, null);

        // T2 groceries: expenses +200, checking -200
        fixtures.insertTransaction("t2", "ledger-1", "Groceries", LocalDate.of(2026, 1, 10));
        fixtures.insertEntry("t2", "expenses", "200.00", "DEBIT", "USD", null, null);
        fixtures.insertEntry("t2", "checking", "200.00", "CREDIT", "USD", null, null);

        // T3 USD->EUR transfer: checking -100 USD, brokerage +90 EUR (converted)
        fixtures.insertTransaction("t3", "ledger-1", "Buy EUR", LocalDate.of(2026, 1, 15));
        fixtures.insertEntry("t3", "brokerage", "100.00", "DEBIT", "USD", "90.00", "EUR");
        fixtures.insertEntry("t3", "checking", "100.00", "CREDIT", "USD", null, null);
    }

    @Test
    void listLedgerAccounts_computesCurrencyAwareBalancesWithSubtreeRollup() {
        var accounts = accountRepository.listLedgerAccounts("ledger-1", PageRequest.of(0, 20)).getContent();

        // checking: ASSET debit-credit = 1000 - (200 + 100) = 700
        assertThat(balanceOf(accounts, "Checking")).isEqualByComparingTo("700.00");
        // assets: parent rolls up itself + checking = 700
        assertThat(balanceOf(accounts, "Assets")).isEqualByComparingTo("700.00");
        // income: CREDIT-DEBIT = 1000
        assertThat(balanceOf(accounts, "Income")).isEqualByComparingTo("1000.00");
        // expenses: DEBIT-CREDIT = 200
        assertThat(balanceOf(accounts, "Expenses")).isEqualByComparingTo("200.00");
        // brokerage (EUR): converted to_amount applies = +90 EUR
        assertThat(balanceOf(accounts, "Brokerage")).isEqualByComparingTo("90.00");
    }

    @Test
    void findById_rollsUpChildBalanceIntoParent() {
        var assets = accountRepository.findById("ledger-1", "assets").orElseThrow();
        assertThat(assets.balance()).isEqualByComparingTo("700.00");

        var checking = accountRepository.findById("ledger-1", "checking").orElseThrow();
        assertThat(checking.balance()).isEqualByComparingTo("700.00");
    }

    @Test
    void getBalanceSummary_sumsLeafAssetLiabilityByCurrency() {
        var summary = accountRepository.getBalanceSummary("ledger-1");

        // Only leaf ASSET/LIABILITY accounts: checking (USD) and brokerage (EUR).
        // assets is excluded (has a child); income/expenses are not ASSET/LIABILITY.
        assertThat(summary.balanceByCurrency().get("USD")).isEqualByComparingTo("700.00");
        assertThat(summary.balanceByCurrency().get("EUR")).isEqualByComparingTo("90.00");
    }

    @Test
    void ledgerView_usesSameCurrencyAwareBalances() {
        var ledger = ledgerRepository.findBySlug("user-1", "main").orElseThrow();

        var assets = ledger.accounts().stream().filter(a -> a.name().equals("Assets")).findFirst().orElseThrow();
        assertThat(assets.balance()).isEqualByComparingTo("700.00");
        var checking = assets.subAccounts().getFirst();
        assertThat(checking.name()).isEqualTo("Checking");
        assertThat(checking.balance()).isEqualByComparingTo("700.00");

        var brokerage = ledger.accounts().stream().filter(a -> a.name().equals("Brokerage")).findFirst().orElseThrow();
        assertThat(brokerage.balance()).isEqualByComparingTo("90.00");
    }

    @Test
    void deletingTransactionEntries_immediatelyUpdatesBalance() {
        // Remove the groceries transaction; checking should rise by 200 (700 -> 900).
        jdbcClient.sql("DELETE FROM transaction_entries WHERE transaction_id = 't2'").update();
        jdbcClient.sql("DELETE FROM transactions WHERE id = 't2'").update();

        var checking = accountRepository.findById("ledger-1", "checking").orElseThrow();
        assertThat(checking.balance()).isEqualByComparingTo("900.00");
        assertThat(balanceOf(accountRepository.listLedgerAccounts("ledger-1", PageRequest.of(0, 20)).getContent(), "Expenses"))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void summaryTableMatchesIndependentRecomputationFromEntries() {
        // The summary debit/credit totals must equal a fresh aggregate of entries
        // (both facets), proving the triggers didn't drift.
        var mismatches = jdbcClient.sql("""
                        WITH expected AS (
                            SELECT account_id, currency,
                                   SUM(CASE WHEN type = 'DEBIT'  THEN amt ELSE 0 END) AS debit_total,
                                   SUM(CASE WHEN type = 'CREDIT' THEN amt ELSE 0 END) AS credit_total
                            FROM (
                                SELECT account_id, currency, type, amount AS amt FROM transaction_entries
                                UNION ALL
                                SELECT account_id, to_currency, type, to_amount
                                FROM transaction_entries
                                WHERE to_currency IS NOT NULL AND to_amount IS NOT NULL
                            ) f
                            GROUP BY account_id, currency
                        )
                        SELECT COUNT(*) FROM expected e
                        FULL OUTER JOIN account_balance_summary s
                          ON s.account_id = e.account_id AND s.currency = e.currency
                        WHERE COALESCE(e.debit_total, 0)  <> COALESCE(s.debit_total, 0)
                           OR COALESCE(e.credit_total, 0) <> COALESCE(s.credit_total, 0)
                        """)
                .query(Long.class)
                .single();

        assertThat(mismatches).isZero();
    }

    private BigDecimal balanceOf(List<Account> accounts, String name) {
        return accounts.stream()
                .filter(a -> a.name().equals(name))
                .findFirst()
                .orElseThrow()
                .balance();
    }

}
