package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack export: seeded PostgreSQL through the Spring-wired service down
 * to the rendered file bytes.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
class TransactionExportServiceIntegrationTest {

    private static final String USER = "user-1";
    private static final String LEDGER_SLUG = "main";

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionExportService exportService;

    @BeforeEach
    void setUp() {
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser(USER);
        fixtures.insertLedger("ledger-1", USER, "Main", "USD");
        fixtures.insertAccount("checking", "ledger-1", null, "Checking", "USD", "ASSET");
        fixtures.insertAccount("savings", "ledger-1", "checking", "Savings", "USD", "ASSET");
        fixtures.insertAccount("income", "ledger-1", null, "Income", "USD", "INCOME");

        fixtures.insertTransaction("t1", "ledger-1", "Salary, \"gross\" & net", LocalDate.of(2026, 1, 10));
        fixtures.insertEntry("t1", "checking", "100.00", "DEBIT", "USD");
        fixtures.insertEntry("t1", "income", "100.00", "CREDIT", "USD");

        fixtures.insertTransaction("t2", "ledger-1", "Interest", LocalDate.of(2026, 2, 15));
        fixtures.insertEntry("t2", "savings", "50.00", "DEBIT", "USD");
        fixtures.insertEntry("t2", "income", "50.00", "CREDIT", "USD");
    }

    @Test
    void csvExport_rendersSeededTransactionsWithEscaping() {
        var file = exportService.export(USER, LEDGER_SLUG,
                new ExportRequest("csv", null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        var csv = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(file.filename()).isEqualTo("main-transactions-2026-01-01_2026-12-31.csv");
        assertThat(file.contentType()).isEqualTo("text/csv; charset=UTF-8");
        assertThat(csv).contains("\"Salary, \"\"gross\"\" & net\"");
        assertThat(csv.split("\r\n")).hasSize(5); // header + 4 entries
        // Ordered by date: t1 entries before t2 entries.
        assertThat(csv.indexOf("t1,")).isLessThan(csv.indexOf("t2,"));
    }

    @Test
    void ofxExport_withSubtree_emitsOneStatementPerAssetAccount() {
        var file = exportService.export(USER, LEDGER_SLUG,
                new ExportRequest("ofx", "checking", true, null, LocalDate.of(2026, 12, 31)));

        var ofx = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(file.filename()).isEqualTo("main-checking-transactions-start_2026-12-31.ofx");
        assertThat(ofx).startsWith("OFXHEADER:100");
        // Both subtree accounts get statements; the income counterpart does not.
        assertThat(ofx).contains("<ACCTID>checking");
        assertThat(ofx).contains("<ACCTID>savings");
        assertThat(ofx).doesNotContain("<ACCTID>income");
        // Statement balances come from the real rollup query.
        assertThat(ofx).contains("<BALAMT>100.00");
        assertThat(ofx).contains("<BALAMT>50.00");
        // The escaped description lands in NAME/MEMO.
        assertThat(ofx).contains("<MEMO>Salary, \"gross\" &amp; net");
    }
}
