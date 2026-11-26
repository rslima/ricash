package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.testsupport.DbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant authorization for transactions: another user's transaction must
 * be unreachable by id, and ids embedded in a request body (envelope,
 * instrument) must belong to the caller's ledger. Every rejected write also
 * asserts the victim's rows and trigger-maintained summaries are untouched.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
@Transactional
class TransactionOwnershipTest {

    private static final String OWNER = "user-a";
    private static final String ATTACKER = "user-b";
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser(OWNER);
        fixtures.insertUser(ATTACKER);
        fixtures.insertLedger("ledger-a", OWNER, "A Main", "USD");
        fixtures.insertLedger("ledger-b", ATTACKER, "B Main", "USD");

        fixtures.insertAccount("acc-a-cash", "ledger-a", null, "A Cash", "USD", "ASSET");
        fixtures.insertAccount("acc-a-food", "ledger-a", null, "A Food", "USD", "EXPENSE");
        fixtures.insertEnvelope("env-a", "ledger-a", "A Groceries", "USD");
        fixtures.insertInstrument("instr-a", "ledger-a", "AAPL", "Apple", "STOCK", "USD");
        fixtures.insertTransaction("tx-a", "ledger-a", "Groceries", DATE);
        fixtures.insertEntry("tx-a", "acc-a-food", "10.00", "DEBIT", "USD", null, null, "env-a");
        fixtures.insertEntry("tx-a", "acc-a-cash", "10.00", "CREDIT", "USD");

        fixtures.insertAccount("acc-b-cash", "ledger-b", null, "B Cash", "USD", "ASSET");
        fixtures.insertAccount("acc-b-food", "ledger-b", null, "B Food", "USD", "EXPENSE");
    }

    @Test
    void otherUser_cannotReadForeignTransaction() throws Exception {
        mockMvc.perform(get("/v1/ledgers/b-main/transactions/tx-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUser_cannotDeleteForeignTransaction() throws Exception {
        mockMvc.perform(delete("/v1/ledgers/b-main/transactions/tx-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());

        assertThat(entryCount("tx-a")).isEqualTo(2);
        assertThat(debitTotal("acc-a-food")).isEqualByComparingTo("10.00");
        assertThat(envelopeSpent("env-a")).isEqualByComparingTo("10.00");
    }

    @Test
    void otherUser_cannotUpdateForeignTransaction() throws Exception {
        mockMvc.perform(put("/v1/ledgers/b-main/transactions/tx-a")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(transactionBody("acc-b-food", "acc-b-cash", "99.00", null, null)))
                .andExpect(status().isNotFound());

        assertThat(entryCount("tx-a")).isEqualTo(2);
        assertThat(debitTotal("acc-a-food")).isEqualByComparingTo("10.00");
    }

    @Test
    void otherUser_cannotBookSpendingIntoForeignEnvelope() throws Exception {
        mockMvc.perform(post("/v1/ledgers/b-main/transactions")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(transactionBody("acc-b-food", "acc-b-cash", "500.00", "env-a", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("Envelope not found")));

        assertThat(envelopeSpent("env-a")).isEqualByComparingTo("10.00");
        assertThat(transactionCount("ledger-b")).isZero();
    }

    @Test
    void otherUser_cannotReferenceForeignInstrument() throws Exception {
        mockMvc.perform(post("/v1/ledgers/b-main/transactions")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(transactionBody("acc-b-food", "acc-b-cash", "50.00", null, "instr-a")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("Instrument not found")));

        assertThat(transactionCount("ledger-b")).isZero();
    }

    @Test
    void owner_canDeleteOwnTransaction() throws Exception {
        mockMvc.perform(delete("/v1/ledgers/a-main/transactions/tx-a")
                        .with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isNoContent());

        assertThat(entryCount("tx-a")).isZero();
        assertThat(transactionCount("ledger-a")).isZero();
    }

    @Test
    void owner_canUseOwnEnvelopeAndInstrument() throws Exception {
        mockMvc.perform(post("/v1/ledgers/a-main/transactions")
                        .with(jwtFor(OWNER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(transactionBody("acc-a-food", "acc-a-cash", "5.00", "env-a", "instr-a")))
                .andExpect(status().isCreated());

        assertThat(envelopeSpent("env-a")).isEqualByComparingTo("15.00");
    }

    @Test
    void amountWithMoreThanTwoDecimals_isRejected() throws Exception {
        // 0.005 + 0.005 vs 0.01 balances in Java but would store as
        // 0.01 + 0.01 vs 0.01 after numeric(20,2) rounding.
        mockMvc.perform(post("/v1/ledgers/a-main/transactions")
                        .with(jwtFor(OWNER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"date":"2026-03-11","description":"Rounding","entries":[
                                  {"accountId":"acc-a-food","amount":0.005,"currency":"USD","type":"DEBIT"},
                                  {"accountId":"acc-a-food","amount":0.005,"currency":"USD","type":"DEBIT"},
                                  {"accountId":"acc-a-cash","amount":0.01,"currency":"USD","type":"CREDIT"}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].source.pointer", containsString("amount")));

        assertThat(transactionCount("ledger-a")).isEqualTo(1);
    }

    @Test
    void filteredList_keepsFiltersInPaginationLinks() throws Exception {
        mockMvc.perform(get("/v1/ledgers/a-main/transactions")
                        .param("accountId", "acc-a-cash")
                        .param("page[size]", "1")
                        .with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                // Unset filters stay as a URI template, exactly as on the
                // unfiltered list (pinned by PaginationLinksGoldenTest).
                .andExpect(jsonPath("$.links.self.href", containsString("accountId=acc-a-cash")))
                .andExpect(jsonPath("$.links.last.href", containsString("accountId=acc-a-cash")));
    }

    private static String transactionBody(String debitAccount, String creditAccount, String amount,
                                          String envelopeId, String instrumentId) {
        String debitExtras = (envelopeId != null ? ",\"envelopeId\":\"" + envelopeId + "\"" : "")
                + (instrumentId != null ? ",\"instrumentId\":\"" + instrumentId + "\",\"quantity\":1" : "");
        return """
                {"date":"2026-03-11","description":"Test","entries":[
                  {"accountId":"%s","amount":%s,"currency":"USD","type":"DEBIT"%s},
                  {"accountId":"%s","amount":%s,"currency":"USD","type":"CREDIT"}
                ]}
                """.formatted(debitAccount, amount, debitExtras, creditAccount, amount);
    }

    private long entryCount(String transactionId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM transaction_entries WHERE transaction_id = :id")
                .param("id", transactionId).query(Long.class).single();
    }

    private long transactionCount(String ledgerId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM transactions WHERE ledger_id = :id")
                .param("id", ledgerId).query(Long.class).single();
    }

    private BigDecimal debitTotal(String accountId) {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(debit_total), 0) FROM account_balance_summary
                        WHERE account_id = :id
                        """)
                .param("id", accountId).query(BigDecimal.class).single();
    }

    private BigDecimal envelopeSpent(String envelopeId) {
        return jdbcClient.sql("""
                        SELECT COALESCE(SUM(spent_total), 0) FROM envelope_monthly_summary
                        WHERE envelope_id = :id
                        """)
                .param("id", envelopeId).query(BigDecimal.class).single();
    }
}
