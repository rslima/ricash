package com.rslima.ricash.ledgers.accounts;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.LedgerJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
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

    @BeforeEach
    void setUp() {
        accountRepository = new AccountJdbcRepository(jdbcClient);
        ledgerRepository = new LedgerJdbcRepository(jdbcClient);

        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();

        jdbcClient.sql("INSERT INTO users (id) VALUES ('user-1')").update();
        insertLedger("ledger-1", "user-1", "Main", "USD");

        // assets (parent) -> checking (child); plus income, expenses, and a EUR brokerage.
        insertAccount("assets", "ledger-1", null, "Assets", "USD", "ASSET");
        insertAccount("checking", "ledger-1", "assets", "Checking", "USD", "ASSET");
        insertAccount("income", "ledger-1", null, "Income", "USD", "INCOME");
        insertAccount("expenses", "ledger-1", null, "Expenses", "USD", "EXPENSE");
        insertAccount("brokerage", "ledger-1", null, "Brokerage", "EUR", "ASSET");

        // T1 salary: checking +1000, income +1000
        insertTransaction("t1", "ledger-1", "Salary", LocalDate.of(2026, 1, 5));
        insertEntry("t1", "checking", "1000.00", "DEBIT", "USD", null, null);
        insertEntry("t1", "income", "1000.00", "CREDIT", "USD", null, null);

        // T2 groceries: expenses +200, checking -200
        insertTransaction("t2", "ledger-1", "Groceries", LocalDate.of(2026, 1, 10));
        insertEntry("t2", "expenses", "200.00", "DEBIT", "USD", null, null);
        insertEntry("t2", "checking", "200.00", "CREDIT", "USD", null, null);

        // T3 USD->EUR transfer: checking -100 USD, brokerage +90 EUR (converted)
        insertTransaction("t3", "ledger-1", "Buy EUR", LocalDate.of(2026, 1, 15));
        insertEntry("t3", "brokerage", "100.00", "DEBIT", "USD", "90.00", "EUR");
        insertEntry("t3", "checking", "100.00", "CREDIT", "USD", null, null);
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

    private void insertLedger(String id, String userId, String name, String currency) {
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, :name, :description, :currency, :createdAt)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("slug", name.toLowerCase())
                .param("name", name)
                .param("description", null)
                .param("currency", currency)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertAccount(String id, String ledgerId, String parentId, String name, String currency, String type) {
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentId, :slug, :name, :description, :currency, :type, 'ACTIVE', :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("parentId", parentId)
                .param("slug", id)
                .param("name", name)
                .param("description", null)
                .param("currency", currency)
                .param("type", type)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertTransaction(String id, String ledgerId, String description, LocalDate date) {
        jdbcClient.sql("""
                        INSERT INTO transactions (id, ledger_id, description, date, created_at)
                        VALUES (:id, :ledgerId, :description, :date, :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("description", description)
                .param("date", Date.valueOf(date))
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertEntry(String transactionId, String accountId, String amount, String type,
                             String currency, String toAmount, String toCurrency) {
        jdbcClient.sql("""
                        INSERT INTO transaction_entries (id, transaction_id, account_id, amount, type, currency, to_amount, to_currency)
                        VALUES (:id, :transactionId, :accountId, :amount, :type, :currency, :toAmount, :toCurrency)
                        """)
                .param("id", java.util.UUID.randomUUID().toString())
                .param("transactionId", transactionId)
                .param("accountId", accountId)
                .param("amount", new BigDecimal(amount))
                .param("type", type)
                .param("currency", currency)
                .param("toAmount", toAmount != null ? new BigDecimal(toAmount) : null)
                .param("toCurrency", toCurrency)
                .update();
    }
}
