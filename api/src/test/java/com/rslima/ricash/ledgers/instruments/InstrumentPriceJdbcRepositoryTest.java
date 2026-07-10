package com.rslima.ricash.ledgers.instruments;

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
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterizes the upsert the external-price feature depends on: a second
 * save for the same (instrument_id, effective_date) must update price/source
 * in place — keeping the original row id — never insert a duplicate.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class InstrumentPriceJdbcRepositoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 9);

    @Autowired
    private JdbcClient jdbcClient;

    private InstrumentPriceJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InstrumentPriceJdbcRepository(jdbcClient);
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertLedger("ledger-1", "user-1", "Main", "USD");
        fixtures.insertInstrument("instr-a", "ledger-1", "IWDA", "iShares World", "ETF", "USD",
                "IE00B4L5Y983", "ACTIVE");
    }

    private InstrumentPrice price(String id, String value, String source) {
        return new InstrumentPrice(id, "instr-a", new BigDecimal(value), DATE, source, Instant.now());
    }

    @Test
    void save_sameInstrumentAndDate_updatesInPlaceAndReturnsOriginalId() {
        var first = repository.save(price("price-1", "102.200000", "YAHOO"));
        var second = repository.save(price("price-2", "103.300000", "YAHOO"));

        // The conflict path keeps the existing row: same id, updated price.
        assertThat(first.id()).isEqualTo("price-1");
        assertThat(second.id()).isEqualTo("price-1");
        assertThat(second.price()).isEqualByComparingTo("103.300000");

        var rows = jdbcClient.sql("SELECT COUNT(*) FROM instrument_prices WHERE instrument_id = 'instr-a'")
                .query(Long.class).single();
        assertThat(rows).isEqualTo(1);

        var stored = jdbcClient.sql("SELECT price FROM instrument_prices WHERE id = 'price-1'")
                .query(BigDecimal.class).single();
        assertThat(stored).isEqualByComparingTo("103.300000");
    }

    @Test
    void save_overwritesSourceOnConflict() {
        repository.save(price("price-1", "100.000000", "MANUAL"));
        var refreshed = repository.save(price("price-2", "101.000000", "YAHOO"));

        assertThat(refreshed.id()).isEqualTo("price-1");
        assertThat(refreshed.source()).isEqualTo("YAHOO");
    }
}
