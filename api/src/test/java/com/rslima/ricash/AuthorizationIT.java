package com.rslima.ricash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.stream.Stream;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Systemic cross-user authorization check: every ledger-scoped read route
 * family must return 404 for a user who does not own the ledger — and 2xx for
 * the owner, proving the 404 comes from the ownership check and not a broken
 * route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
@Transactional
class AuthorizationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    private static final String OWNER = "owner-user";
    private static final String ATTACKER = "attacker-user";
    private static final String SLUG = "owner-ledger";

    static Stream<String> ledgerScopedReadRoutes() {
        return Stream.of(
                "/v1/ledgers/" + SLUG,
                "/v1/ledgers/" + SLUG + "/accounts",
                "/v1/ledgers/" + SLUG + "/accounts/account-1",
                "/v1/ledgers/" + SLUG + "/accounts/balance-summary",
                "/v1/ledgers/" + SLUG + "/transactions",
                "/v1/ledgers/" + SLUG + "/transactions/tx-1",
                "/v1/ledgers/" + SLUG + "/transactions/monthly-report?year=2026&month=3",
                "/v1/ledgers/" + SLUG + "/envelopes",
                "/v1/ledgers/" + SLUG + "/envelopes/envelope-1",
                "/v1/ledgers/" + SLUG + "/budget?year=2026&month=3",
                "/v1/ledgers/" + SLUG + "/instruments",
                "/v1/ledgers/" + SLUG + "/instruments/instrument-1",
                "/v1/ledgers/" + SLUG + "/instruments/all",
                "/v1/ledgers/" + SLUG + "/instrument-prices",
                "/v1/ledgers/" + SLUG + "/instrument-prices?instrumentId=instrument-1",
                "/v1/ledgers/" + SLUG + "/portfolio"
        );
    }

    @BeforeEach
    void seedOwnerData() {
        jdbcClient.sql("DELETE FROM instrument_prices").update();
        jdbcClient.sql("DELETE FROM transaction_entries").update();
        jdbcClient.sql("DELETE FROM transactions").update();
        jdbcClient.sql("DELETE FROM envelope_allocations").update();
        jdbcClient.sql("DELETE FROM envelopes").update();
        jdbcClient.sql("DELETE FROM instruments").update();
        jdbcClient.sql("DELETE FROM accounts").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();

        var now = Timestamp.from(Instant.now());
        jdbcClient.sql("INSERT INTO users (id) VALUES (:id)").param("id", OWNER).update();
        jdbcClient.sql("INSERT INTO users (id) VALUES (:id)").param("id", ATTACKER).update();
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES ('ledger-1', :owner, :slug, 'Owner Ledger', NULL, 'USD', :now)
                        """)
                .param("owner", OWNER).param("slug", SLUG).param("now", now).update();
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES ('account-1', 'ledger-1', NULL, 'checking', 'Checking', NULL, 'USD', 'ASSET', 'ACTIVE', :now)
                        """)
                .param("now", now).update();
        jdbcClient.sql("""
                        INSERT INTO transactions (id, ledger_id, date, description, created_at)
                        VALUES ('tx-1', 'ledger-1', '2026-03-10', 'Seed', :now)
                        """)
                .param("now", now).update();
        jdbcClient.sql("""
                        INSERT INTO envelopes (id, ledger_id, parent_envelope_id, name, description, currency, type, status, created_at)
                        VALUES ('envelope-1', 'ledger-1', NULL, 'Food', NULL, 'USD', 'EXPENSE', 'ACTIVE', :now)
                        """)
                .param("now", now).update();
        jdbcClient.sql("""
                        INSERT INTO instruments (id, ledger_id, symbol, name, type, currency, market, isin, status, created_at)
                        VALUES ('instrument-1', 'ledger-1', 'PETR4', 'Petrobras', 'STOCK', 'BRL', NULL, NULL, 'ACTIVE', :now)
                        """)
                .param("now", now).update();
    }

    @ParameterizedTest
    @MethodSource("ledgerScopedReadRoutes")
    void ownerCanRead(String route) throws Exception {
        mockMvc.perform(get(route)
                        .with(jwt().jwt(b -> b.claim("preferred_username", OWNER)))
                        .accept(JSON_API_VALUE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest
    @MethodSource("ledgerScopedReadRoutes")
    void nonOwnerGets404(String route) throws Exception {
        mockMvc.perform(get(route)
                        .with(jwt().jwt(b -> b.claim("preferred_username", ATTACKER)))
                        .accept(JSON_API_VALUE, org.springframework.http.MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isNotFound());
    }
}
