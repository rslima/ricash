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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterizes the system-wide enumeration backing the scheduled price
 * refresh: ACTIVE instruments with a non-blank ISIN, across every ledger.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class InstrumentJdbcRepositoryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private InstrumentJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InstrumentJdbcRepository(jdbcClient);
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertUser("user-2");
        fixtures.insertLedger("ledger-1", "user-1", "Main", "USD");
        fixtures.insertLedger("ledger-2", "user-2", "Other", "EUR");

        fixtures.insertInstrument("with-isin-1", "ledger-1", "IWDA", "iShares World", "ETF", "USD",
                "IE00B4L5Y983", "ACTIVE");
        fixtures.insertInstrument("with-isin-2", "ledger-2", "VWCE", "Vanguard All-World", "ETF", "EUR",
                "IE00BK5BQT80", "ACTIVE");
        fixtures.insertInstrument("no-isin", "ledger-1", "AAPL", "Apple", "STOCK", "USD");
        fixtures.insertInstrument("blank-isin", "ledger-1", "MSFT", "Microsoft", "STOCK", "USD",
                "", "ACTIVE");
        fixtures.insertInstrument("inactive", "ledger-1", "GOOG", "Alphabet", "STOCK", "USD",
                "US02079K3059", "INACTIVE");
    }

    @Test
    void findAllActiveWithIsinSystemWide_spansLedgersAndFiltersStatusAndIsin() {
        var instruments = repository.findAllActiveWithIsinSystemWide();

        assertThat(instruments).extracting(Instrument::id)
                .containsExactly("with-isin-1", "with-isin-2");
        assertThat(instruments).extracting(Instrument::ledgerId)
                .containsExactly("ledger-1", "ledger-2");
    }
}
