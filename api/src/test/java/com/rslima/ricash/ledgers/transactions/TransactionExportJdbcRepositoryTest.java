package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRicashApplication.class)
class TransactionExportJdbcRepositoryTest {

    private static final String LEDGER = "ledger-1";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertLedger(LEDGER, "user-1", "Main", "USD");
        fixtures.insertAccount("checking", LEDGER, null, "Checking", "USD", "ASSET");
        fixtures.insertAccount("savings", LEDGER, "checking", "Savings", "USD", "ASSET");
        fixtures.insertAccount("income", LEDGER, null, "Income", "USD", "INCOME");
        fixtures.insertAccount("eur-cash", LEDGER, null, "EUR Cash", "EUR", "ASSET");

        fixtures.insertTransaction("t1", LEDGER, "Salary", LocalDate.of(2026, 1, 10));
        fixtures.insertEntry("t1", "checking", "100.00", "DEBIT", "USD");
        fixtures.insertEntry("t1", "income", "100.00", "CREDIT", "USD");

        fixtures.insertTransaction("t2", LEDGER, "Interest", LocalDate.of(2026, 1, 15));
        fixtures.insertEntry("t2", "savings", "50.00", "DEBIT", "USD");
        fixtures.insertEntry("t2", "income", "50.00", "CREDIT", "USD");

        // Entry-less transaction: must not appear in the export at all.
        fixtures.insertTransaction("t3", LEDGER, "Empty", LocalDate.of(2026, 1, 20));

        // Multi-currency: USD amount converted into the EUR account's currency.
        fixtures.insertTransaction("t4", LEDGER, "FX top-up", LocalDate.of(2026, 1, 31));
        fixtures.insertEntry("t4", "eur-cash", "10.00", "DEBIT", "USD", "9.00", "EUR");
        fixtures.insertEntry("t4", "income", "10.00", "CREDIT", "USD");

        fixtures.insertTransaction("t5", LEDGER, "February salary", LocalDate.of(2026, 2, 1));
        fixtures.insertEntry("t5", "checking", "25.00", "DEBIT", "USD");
        fixtures.insertEntry("t5", "income", "25.00", "CREDIT", "USD");

        // Another user's ledger: must never leak into ledger-1 exports.
        fixtures.insertUser("user-2");
        fixtures.insertLedger("ledger-2", "user-2", "Other", "USD");
        fixtures.insertAccount("other-checking", "ledger-2", null, "Checking", "USD", "ASSET");
        fixtures.insertTransaction("tx-other", "ledger-2", "Foreign", LocalDate.of(2026, 1, 10));
        fixtures.insertEntry("tx-other", "other-checking", "999.00", "DEBIT", "USD");
    }

    @Test
    void listEntriesForExport_unbounded_returnsAllEntriesOrderedByDate() {
        var rows = transactionRepository.listEntriesForExport(LEDGER, null, null, null);

        assertThat(rows).hasSize(8);
        assertThat(rows.stream().map(TransactionExportRow::transactionId).distinct())
                .containsExactly("t1", "t2", "t4", "t5");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.entryId()).isNotNull();
            assertThat(row.accountName()).isNotNull();
        });
    }

    @Test
    void listEntriesForExport_dateBounds_fromInclusiveToExclusive() {
        var rows = transactionRepository.listEntriesForExport(
                LEDGER, null, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 1));

        assertThat(rows.stream().map(TransactionExportRow::transactionId).distinct())
                .containsExactly("t2", "t4");
    }

    @Test
    void listEntriesForExport_accountScope_returnsWholeTransactions() {
        var rows = transactionRepository.listEntriesForExport(LEDGER, List.of("checking"), null, null);

        assertThat(rows.stream().map(TransactionExportRow::transactionId).distinct())
                .containsExactly("t1", "t5");
        // Counterpart entries on other accounts are included.
        assertThat(rows.stream().filter(r -> r.transactionId().equals("t1")))
                .extracting(TransactionExportRow::accountId)
                .containsExactlyInAnyOrder("checking", "income");
    }

    @Test
    void listEntriesForExport_multipleAccounts_coversSubtreeScope() {
        var rows = transactionRepository.listEntriesForExport(
                LEDGER, List.of("checking", "savings"), null, null);

        assertThat(rows.stream().map(TransactionExportRow::transactionId).distinct())
                .containsExactly("t1", "t2", "t5");
    }

    @Test
    void listEntriesForExport_accountAndDateRange_appliesAllPredicates() {
        var rows = transactionRepository.listEntriesForExport(
                LEDGER, List.of("checking", "savings"), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 1));

        // t1 (2026-01-10) is excluded by :from, t4 (eur-cash only) by
        // :accountIds, t5 (2026-02-01) by :toExclusive — only t2 survives all
        // three concatenated predicates.
        assertThat(rows.stream().map(TransactionExportRow::transactionId).distinct())
                .containsExactly("t2");
    }

    @Test
    void listEntriesForExport_mapsEntryFields() {
        var rows = transactionRepository.listEntriesForExport(
                LEDGER, List.of("eur-cash"), null, null);

        var converted = rows.stream().filter(r -> r.accountId().equals("eur-cash")).findFirst().orElseThrow();
        assertThat(converted.type()).isEqualTo(TransactionEntryType.DEBIT);
        assertThat(converted.amount()).isEqualByComparingTo("10.00");
        assertThat(converted.currency()).isEqualTo("USD");
        assertThat(converted.toAmount()).isEqualByComparingTo("9.00");
        assertThat(converted.toCurrency()).isEqualTo("EUR");
        assertThat(converted.accountName()).isEqualTo("EUR Cash");
        assertThat(converted.description()).isEqualTo("FX top-up");
        assertThat(converted.date()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void getAccountBalancesAsOf_sumsDebitsMinusCreditsInAccountCurrency() {
        var balances = transactionRepository.getAccountBalancesAsOf(
                LEDGER, List.of("checking", "income", "eur-cash"), null);

        assertThat(balances.get("checking")).isEqualByComparingTo("125.00");
        assertThat(balances.get("income")).isEqualByComparingTo("-185.00");
        // Converted amount is used because to_currency matches the account currency.
        assertThat(balances.get("eur-cash")).isEqualByComparingTo("9.00");
    }

    @Test
    void getAccountBalancesAsOf_respectsDateCutoff() {
        var balances = transactionRepository.getAccountBalancesAsOf(
                LEDGER, List.of("checking"), LocalDate.of(2026, 2, 1));

        assertThat(balances.get("checking")).isEqualByComparingTo("100.00");
    }

    @Test
    void getAccountBalancesAsOf_emptyAccounts_returnsEmptyMap() {
        assertThat(transactionRepository.getAccountBalancesAsOf(LEDGER, Set.of(), null)).isEmpty();
        assertThat(transactionRepository.getAccountBalancesAsOf(LEDGER, null, null)).isEmpty();
    }
}
