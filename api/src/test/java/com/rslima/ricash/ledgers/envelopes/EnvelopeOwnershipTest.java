package com.rslima.ricash.ledgers.envelopes;

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
 * Cross-tenant authorization for envelopes: foreign envelopes are unreachable
 * by URL id, and ids in request bodies (parent envelope, mapped accounts) must
 * belong to the caller's ledger. Rejected writes leave the victim's rows as
 * they were.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
@Transactional
class EnvelopeOwnershipTest {

    private static final String OWNER = "user-a";
    private static final String ATTACKER = "user-b";

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

        fixtures.insertAccount("acc-a-food", "ledger-a", null, "A Food", "USD", "EXPENSE");
        fixtures.insertEnvelope("env-a", "ledger-a", "A Groceries", "USD");
        fixtures.insertEnvelopeAccountMapping("env-a", "acc-a-food");
        fixtures.insertEnvelopeAllocation("env-a", 2026, 3, "200.00");

        fixtures.insertEnvelope("env-b", "ledger-b", "B Groceries", "USD");
    }

    @Test
    void otherUser_cannotReadForeignEnvelopeBalance() throws Exception {
        mockMvc.perform(get("/v1/ledgers/b-main/envelopes/env-a/balance")
                        .param("year", "2026").param("month", "3")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUser_cannotUpdateForeignEnvelope() throws Exception {
        mockMvc.perform(put("/v1/ledgers/b-main/envelopes/env-a")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"name":"Hacked","type":"EXPENSE","currency":"USD","status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());

        assertThat(name("env-a")).isEqualTo("A Groceries");
    }

    @Test
    void otherUser_cannotDeleteForeignEnvelope() throws Exception {
        mockMvc.perform(delete("/v1/ledgers/b-main/envelopes/env-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());

        assertThat(name("env-a")).isEqualTo("A Groceries");
        assertThat(mappingCount("acc-a-food")).isEqualTo(1);
    }

    @Test
    void otherUser_cannotAllocateToForeignEnvelope() throws Exception {
        mockMvc.perform(post("/v1/ledgers/b-main/envelopes/env-a/allocations")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"year":2026,"month":3,"allocatedAmount":0}
                                """))
                .andExpect(status().isNotFound());

        assertThat(allocated("env-a", 2026, 3)).isEqualTo("200.00");
    }

    @Test
    void otherUser_cannotStealForeignAccountMapping() throws Exception {
        // The mapping rewrite deletes existing mappings by account id; with a
        // foreign account id it used to delete the owner's mapping.
        mockMvc.perform(put("/v1/ledgers/b-main/envelopes/env-b/accounts")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("[\"acc-a-food\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("Account not found")));

        assertThat(mappedEnvelope("acc-a-food")).isEqualTo("env-a");
    }

    @Test
    void otherUser_cannotCreateEnvelopeUnderForeignParent() throws Exception {
        mockMvc.perform(post("/v1/ledgers/b-main/envelopes")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"name":"Sneaky","currency":"USD","type":"EXPENSE","parentEnvelopeId":"env-a"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("Parent envelope not found")));

        assertThat(envelopeCount("ledger-b")).isEqualTo(1);
    }

    @Test
    void owner_canRemapOwnAccount() throws Exception {
        mockMvc.perform(put("/v1/ledgers/a-main/envelopes/env-a/accounts")
                        .with(jwtFor(OWNER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("[\"acc-a-food\"]"))
                .andExpect(status().isOk());

        assertThat(mappedEnvelope("acc-a-food")).isEqualTo("env-a");
    }

    @Test
    void allocationWithMoreThanTwoDecimals_isRejected() throws Exception {
        mockMvc.perform(post("/v1/ledgers/a-main/envelopes/env-a/allocations")
                        .with(jwtFor(OWNER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"year":2026,"month":3,"allocatedAmount":10.555}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(allocated("env-a", 2026, 3)).isEqualTo("200.00");
    }

    private String name(String envelopeId) {
        return jdbcClient.sql("SELECT name FROM envelopes WHERE id = :id")
                .param("id", envelopeId).query(String.class).single();
    }

    private long envelopeCount(String ledgerId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM envelopes WHERE ledger_id = :id")
                .param("id", ledgerId).query(Long.class).single();
    }

    private long mappingCount(String accountId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM envelope_account_mappings WHERE account_id = :id")
                .param("id", accountId).query(Long.class).single();
    }

    private String mappedEnvelope(String accountId) {
        return jdbcClient.sql("SELECT envelope_id FROM envelope_account_mappings WHERE account_id = :id")
                .param("id", accountId).query(String.class).optional().orElse(null);
    }

    private String allocated(String envelopeId, int year, int month) {
        return jdbcClient.sql("""
                        SELECT allocated_amount::text FROM envelope_allocations
                        WHERE envelope_id = :id AND period_year = :year AND period_month = :month
                        """)
                .param("id", envelopeId).param("year", year).param("month", month)
                .query(String.class).single();
    }
}
