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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterizes the position-aggregation SQL (buys as DEBIT, sells as CREDIT,
 * converted amounts preferred) that was moved out of PortfolioServiceBean.
 */
@SpringBootTest
@Import(TestRicashApplication.class)
@Transactional
class PortfolioRepositoryTest {

    @Autowired
    private JdbcClient jdbcClient;

    private PortfolioJdbcRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PortfolioJdbcRepository(jdbcClient);
        var fixtures = new DbFixtures(jdbcClient);
        fixtures.cleanAll();
        fixtures.insertUser("user-1");
        fixtures.insertLedger("ledger-1", "user-1", "Main", "USD");
        fixtures.insertAccount("brokerage", "ledger-1", null, "Brokerage", "USD", "ASSET");
        fixtures.insertAccount("cash", "ledger-1", null, "Cash", "USD", "ASSET");
        fixtures.insertInstrument("instr-a", "ledger-1", "AAPL", "Apple", "STOCK", "USD");

        // Buy 10 @ 100
        fixtures.insertTransaction("t1", "ledger-1", "Buy AAPL", LocalDate.of(2026, 1, 5));
        fixtures.insertInstrumentEntry("t1", "brokerage", "1000.00", "DEBIT", "USD", "instr-a", "10");
        fixtures.insertEntry("t1", "cash", "1000.00", "CREDIT", "USD");

        // Sell 4 @ 120
        fixtures.insertTransaction("t2", "ledger-1", "Sell AAPL", LocalDate.of(2026, 2, 5));
        fixtures.insertInstrumentEntry("t2", "brokerage", "480.00", "CREDIT", "USD", "instr-a", "4");
        fixtures.insertEntry("t2", "cash", "480.00", "DEBIT", "USD");
    }

    @Test
    void aggregatePositions_perLedger_sumsQuantitiesAndAmountsByType() {
        var positions = repository.aggregatePositions("ledger-1");

        assertThat(positions).hasSize(1);
        var position = positions.getFirst();
        assertThat(position.instrumentId()).isEqualTo("instr-a");
        assertThat(position.debitQuantity()).isEqualByComparingTo("10");
        assertThat(position.creditQuantity()).isEqualByComparingTo("4");
        assertThat(position.debitAmount()).isEqualByComparingTo("1000.00");
        assertThat(position.creditAmount()).isEqualByComparingTo("480.00");
    }

    @Test
    void aggregatePositions_perAccount_filtersByAccount() {
        assertThat(repository.aggregatePositions("ledger-1", "brokerage")).hasSize(1);
        // cash entries carry no instrument, so they never aggregate.
        assertThat(repository.aggregatePositions("ledger-1", "cash")).isEmpty();
    }

    @Test
    void aggregatePositions_unknownLedger_isEmpty() {
        assertThat(repository.aggregatePositions("nope")).isEmpty();
    }
}
