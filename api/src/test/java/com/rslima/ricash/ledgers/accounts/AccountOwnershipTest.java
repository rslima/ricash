package com.rslima.ricash.ledgers.accounts;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant authorization and tree integrity for accounts: foreign accounts
 * are unreachable by URL id, a parent id in the body must belong to the
 * caller's ledger, and no update may create a parent cycle (which used to make
 * the recursive account-tree queries run forever).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
@Transactional
class AccountOwnershipTest {

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

        fixtures.insertAccount("acc-a-root", "ledger-a", null, "A Expenses", "USD", "EXPENSE");
        fixtures.insertAccount("acc-a-child", "ledger-a", "acc-a-root", "A Food", "USD", "EXPENSE");
        fixtures.insertAccount("acc-b", "ledger-b", null, "B Expenses", "USD", "EXPENSE");
    }

    @Test
    void otherUser_cannotUpdateForeignAccount() throws Exception {
        mockMvc.perform(put("/v1/ledgers/b-main/accounts/acc-a-root")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(updateBody("Hacked", null)))
                .andExpect(status().isNotFound());

        assertThat(name("acc-a-root")).isEqualTo("A Expenses");
    }

    @Test
    void otherUser_cannotDeleteForeignAccount() throws Exception {
        mockMvc.perform(delete("/v1/ledgers/b-main/accounts/acc-a-child")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());

        assertThat(exists("acc-a-child")).isTrue();
    }

    @Test
    void otherUser_cannotCreateAccountUnderForeignParent() throws Exception {
        mockMvc.perform(post("/v1/ledgers/b-main/accounts")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"name":"Sneaky","currency":"USD","type":"EXPENSE","parentAccountId":"acc-a-root"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("Parent account not found")));

        assertThat(accountCount("ledger-b")).isEqualTo(1);
    }

    @Test
    void otherUser_cannotMoveOwnAccountUnderForeignParent() throws Exception {
        mockMvc.perform(put("/v1/ledgers/b-main/accounts/acc-b")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(updateBody("B Expenses", "acc-a-root")))
                .andExpect(status().isBadRequest());

        assertThat(parent("acc-b")).isNull();
    }

    @Test
    void selfParent_isRejected_andLedgerStaysReadable() throws Exception {
        mockMvc.perform(put("/v1/ledgers/a-main/accounts/acc-a-root")
                        .with(jwtFor(OWNER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(updateBody("A Expenses", "acc-a-root")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("own parent")));

        assertThat(parent("acc-a-root")).isNull();
        mockMvc.perform(get("/v1/ledgers").with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk());
    }

    @Test
    void moveUnderOwnDescendant_isRejected() throws Exception {
        mockMvc.perform(put("/v1/ledgers/a-main/accounts/acc-a-root")
                        .with(jwtFor(OWNER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(updateBody("A Expenses", "acc-a-child")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", containsString("descendants")));

        assertThat(parent("acc-a-root")).isNull();
    }

    @Test
    void storedCycle_doesNotHangTreeQueries() throws Exception {
        // A two-account cycle written before the service checks existed (V15
        // only forbids the self case). The account-tree CTEs must terminate.
        // The statement timeout turns a regression into a fast failure
        // instead of a hung build; it is scoped to this test's transaction.
        jdbcClient.sql("SET LOCAL statement_timeout = '5s'").update();
        jdbcClient.sql("UPDATE accounts SET parent_account_id = 'acc-a-child' WHERE id = 'acc-a-root'").update();

        mockMvc.perform(get("/v1/ledgers").with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/ledgers/a-main/accounts").with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/ledgers/a-main/accounts/acc-a-root").with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk());
        // Other users' ledger lists used to scan every account in the database.
        mockMvc.perform(get("/v1/ledgers").with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk());
    }

    @Test
    void selfParent_isRejectedByDatabaseConstraint() {
        var update = jdbcClient.sql("UPDATE accounts SET parent_account_id = id WHERE id = 'acc-a-root'");

        assertThatThrownBy(update::update)
                .hasMessageContaining("chk_accounts_not_own_parent");
    }

    private static String updateBody(String name, String parentAccountId) {
        String parent = parentAccountId == null ? "null" : "\"" + parentAccountId + "\"";
        return """
                {"name":"%s","type":"EXPENSE","currency":"USD","parentAccountId":%s}
                """.formatted(name, parent);
    }

    private String name(String accountId) {
        return jdbcClient.sql("SELECT name FROM accounts WHERE id = :id")
                .param("id", accountId).query(String.class).single();
    }

    private String parent(String accountId) {
        return jdbcClient.sql("SELECT parent_account_id FROM accounts WHERE id = :id")
                .param("id", accountId).query(String.class).optional().orElse(null);
    }

    private boolean exists(String accountId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM accounts WHERE id = :id")
                .param("id", accountId).query(Long.class).single() > 0;
    }

    private long accountCount(String ledgerId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM accounts WHERE ledger_id = :id")
                .param("id", ledgerId).query(Long.class).single();
    }
}
