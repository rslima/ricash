package com.rslima.ricash.ledgers.instruments;

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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant authorization for the instruments domain: a user must never be
 * able to read, modify or delete another user's instruments or prices by id,
 * regardless of which ledger slug they put in the URL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
@Transactional
class InstrumentOwnershipTest {

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
        fixtures.insertInstrument("instr-a", "ledger-a", "AAPL", "Apple", "STOCK", "USD");
        fixtures.insertInstrumentPrice("price-a", "instr-a", "123.45", java.time.LocalDate.of(2026, 1, 5));
    }

    @Test
    void owner_canReadOwnInstrument() throws Exception {
        mockMvc.perform(get("/v1/ledgers/a-main/instruments/instr-a")
                        .with(jwtFor(OWNER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.symbol", is("AAPL")));
    }

    @Test
    void otherUser_cannotReadForeignInstrumentThroughOwnLedger() throws Exception {
        mockMvc.perform(get("/v1/ledgers/b-main/instruments/instr-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherUser_cannotUpdateForeignInstrument() throws Exception {
        mockMvc.perform(put("/v1/ledgers/b-main/instruments/instr-a")
                        .with(jwtFor(ATTACKER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("""
                                {"symbol":"HACK","name":"Hacked","type":"STOCK","currency":"USD","status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());

        var symbol = jdbcClient.sql("SELECT symbol FROM instruments WHERE id = 'instr-a'")
                .query(String.class).single();
        assertThat(symbol).isEqualTo("AAPL");
    }

    @Test
    void otherUser_cannotDeleteForeignInstrument() throws Exception {
        mockMvc.perform(delete("/v1/ledgers/b-main/instruments/instr-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());

        var count = jdbcClient.sql("SELECT COUNT(*) FROM instruments WHERE id = 'instr-a'")
                .query(Long.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void otherUser_cannotDeleteForeignPrice() throws Exception {
        mockMvc.perform(delete("/v1/ledgers/b-main/instrument-prices/price-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());

        var count = jdbcClient.sql("SELECT COUNT(*) FROM instrument_prices WHERE id = 'price-a'")
                .query(Long.class).single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void otherUser_cannotListForeignInstrumentPrices() throws Exception {
        mockMvc.perform(get("/v1/ledgers/b-main/instrument-prices")
                        .param("instrumentId", "instr-a")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownLedgerSlug_onInstruments_returns404() throws Exception {
        mockMvc.perform(get("/v1/ledgers/ghost/instruments")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownLedgerSlug_onPortfolio_returns404() throws Exception {
        mockMvc.perform(get("/v1/ledgers/ghost/portfolio")
                        .with(jwtFor(ATTACKER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());
    }
}
