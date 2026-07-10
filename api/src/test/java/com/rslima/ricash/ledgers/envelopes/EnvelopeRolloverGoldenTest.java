package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GOLDEN MASTER for the envelope rollover math. The expected values below were
 * hand-derived from the original month-by-month Java recursion and verified
 * green against it BEFORE the single-query rewrite. They pin every semantic
 * corner of the algorithm:
 *
 *  - rollover carries forward only positive leftovers (overspend clamps to 0)
 *  - a month with neither allocation nor spending BREAKS the chain: nothing
 *    rolls across a gap, even from further back
 *  - a month with spending but no allocation still keeps the chain alive
 *  - envelopes with no history at all yield all-zero balances
 *
 * Any implementation change that alters one of these numbers changes users'
 * budget figures - do not "fix" this test to match new output without
 * deciding that deliberately.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class EnvelopeRolloverGoldenTest {

    private static final String USER = "user-1";
    private static final String SLUG = "main";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EnvelopeService envelopeService;

    @BeforeEach
    void setUp() {
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser(USER);
        fixtures.insertLedger("ledger-1", USER, "Main", "USD");
        fixtures.insertAccount("expenses", "ledger-1", null, "Expenses", "USD", "EXPENSE");
        fixtures.insertAccount("checking", "ledger-1", null, "Checking", "USD", "ASSET");
        fixtures.insertAccount("salary", "ledger-1", null, "Salary", "USD", "INCOME");

        // --- Envelope A "Groceries": continuous Jan-Jun history with an
        // overspent month (Feb) and a spend-only month (Apr).
        //   Jan: alloc 100, spent  40 -> avail  60
        //   Feb: alloc 100, spent 190 -> avail 60+100-190 = -30 (clamps to 0)
        //   Mar: alloc  50, spent  20 -> avail  0+50-20  = 30
        //   Apr: alloc   -, spent  10 -> avail 30+0-10   = 20   (chain survives)
        //   May: alloc  80, spent   0 -> avail 20+80     = 100
        //   Jun: alloc 120, spent  30 -> rollover 100, avail 190
        fixtures.insertEnvelope("env-groceries", "ledger-1", "Groceries", "USD");
        fixtures.insertEnvelopeAllocation("env-groceries", 2026, 1, "100.00");
        fixtures.insertEnvelopeAllocation("env-groceries", 2026, 2, "100.00");
        fixtures.insertEnvelopeAllocation("env-groceries", 2026, 3, "50.00");
        fixtures.insertEnvelopeAllocation("env-groceries", 2026, 5, "80.00");
        fixtures.insertEnvelopeAllocation("env-groceries", 2026, 6, "120.00");
        spend("g1", "env-groceries", "40.00", LocalDate.of(2026, 1, 10));
        spend("g2", "env-groceries", "190.00", LocalDate.of(2026, 2, 10));
        spend("g3", "env-groceries", "20.00", LocalDate.of(2026, 3, 10));
        spend("g4", "env-groceries", "10.00", LocalDate.of(2026, 4, 10));
        spend("g5", "env-groceries", "30.00", LocalDate.of(2026, 6, 10));

        // --- Envelope B "Vacation": gap months truncate the rollover chain.
        //   Jan: alloc 200, spent  0 -> avail 200
        //   Feb: (no activity - gap)
        //   Mar: alloc  50, spent 10 -> rollover 0 (gap!), avail 40
        //   Apr: alloc   -, spent 15 -> avail 40-15 = 25
        //   May: (no activity - gap)
        //   Jun: alloc  30            -> rollover 0 (gap!), avail 30
        fixtures.insertEnvelope("env-vacation", "ledger-1", "Vacation", "USD");
        fixtures.insertEnvelopeAllocation("env-vacation", 2026, 1, "200.00");
        fixtures.insertEnvelopeAllocation("env-vacation", 2026, 3, "50.00");
        fixtures.insertEnvelopeAllocation("env-vacation", 2026, 6, "30.00");
        spend("v1", "env-vacation", "10.00", LocalDate.of(2026, 3, 10));
        spend("v2", "env-vacation", "15.00", LocalDate.of(2026, 4, 10));

        // --- Envelope C "Emergency": first-ever activity in the target month.
        fixtures.insertEnvelope("env-emergency", "ledger-1", "Emergency", "USD");
        fixtures.insertEnvelopeAllocation("env-emergency", 2026, 6, "75.00");

        // --- Envelope D "Dormant": no allocations, no spending, ever.
        fixtures.insertEnvelope("env-dormant", "ledger-1", "Dormant", "USD");

        // --- Income in June for getToBeBudgeted: 1000 credited to an INCOME account.
        fixtures.insertTransaction("t-salary", "ledger-1", "Salary", LocalDate.of(2026, 6, 15));
        fixtures.insertEntry("t-salary", "salary", "1000.00", "CREDIT", "USD");
        fixtures.insertEntry("t-salary", "checking", "1000.00", "DEBIT", "USD");
    }

    private void spend(String txId, String envelopeId, String amount, LocalDate date) {
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.insertTransaction(txId, "ledger-1", "spend " + txId, date);
        fixtures.insertEntry(txId, "expenses", amount, "DEBIT", "USD", null, null, envelopeId);
        fixtures.insertEntry(txId, "checking", amount, "CREDIT", "USD");
    }

    @Test
    void budgetSummary_june2026_matchesGoldenValues() {
        var balances = envelopeService.getBudgetSummary(USER, SLUG, 2026, 6);

        assertThat(balances).hasSize(4);
        Map<String, EnvelopeBalance> byId = balances.stream()
                .collect(Collectors.toMap(EnvelopeBalance::envelopeId, Function.identity()));

        assertBalance(byId.get("env-groceries"), "100.00", "120.00", "30.00", "190.00");
        assertBalance(byId.get("env-vacation"), "0.00", "30.00", "0.00", "30.00");
        assertBalance(byId.get("env-emergency"), "0.00", "75.00", "0.00", "75.00");
        assertBalance(byId.get("env-dormant"), "0.00", "0.00", "0.00", "0.00");
    }

    @Test
    void budgetSummary_keepsEnvelopeOrderingByTypeAndName() {
        var balances = envelopeService.getBudgetSummary(USER, SLUG, 2026, 6);

        assertThat(balances).extracting(EnvelopeBalance::envelopeId)
                .containsExactly("env-dormant", "env-emergency", "env-groceries", "env-vacation");
    }

    @Test
    void singleBalance_overspentMonth_exposesNegativeAvailable() {
        // February itself: rollover 60 from January, then overspend.
        var feb = envelopeService.getBalance(USER, SLUG, "env-groceries", 2026, 2);
        assertBalance(feb, "60.00", "100.00", "190.00", "-30.00");
    }

    @Test
    void singleBalance_afterOverspend_clampsRolloverToZero() {
        var march = envelopeService.getBalance(USER, SLUG, "env-groceries", 2026, 3);
        assertBalance(march, "0.00", "50.00", "20.00", "30.00");
    }

    @Test
    void singleBalance_spendOnlyMonth_keepsChainAlive() {
        var april = envelopeService.getBalance(USER, SLUG, "env-groceries", 2026, 4);
        assertBalance(april, "30.00", "0.00", "10.00", "20.00");
    }

    @Test
    void singleBalance_gapMonthBreaksChain() {
        // March vacation: January's 200 leftover must NOT survive the February gap.
        var march = envelopeService.getBalance(USER, SLUG, "env-vacation", 2026, 3);
        assertBalance(march, "0.00", "50.00", "10.00", "40.00");
    }

    @Test
    void toBeBudgeted_isIncomeMinusAllocations() {
        // 1000 income - (120 + 30 + 75) allocated in June
        assertThat(envelopeService.getToBeBudgeted(USER, SLUG, 2026, 6))
                .isEqualByComparingTo("775.00");
    }

    private void assertBalance(EnvelopeBalance balance, String rollover, String allocated, String spent, String available) {
        assertThat(balance).isNotNull();
        assertThat(balance.rollover()).as("rollover").isEqualByComparingTo(new BigDecimal(rollover));
        assertThat(balance.allocated()).as("allocated").isEqualByComparingTo(new BigDecimal(allocated));
        assertThat(balance.spent()).as("spent").isEqualByComparingTo(new BigDecimal(spent));
        assertThat(balance.available()).as("available").isEqualByComparingTo(new BigDecimal(available));
    }
}
