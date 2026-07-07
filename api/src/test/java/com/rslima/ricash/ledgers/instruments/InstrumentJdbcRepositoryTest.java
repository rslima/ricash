package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

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

        jdbcClient.sql("DELETE FROM instrument_prices").update();
        jdbcClient.sql("DELETE FROM instruments").update();
        jdbcClient.sql("DELETE FROM ledgers").update();
        jdbcClient.sql("DELETE FROM users").update();

        jdbcClient.sql("INSERT INTO users (id) VALUES ('user-1')").update();
        jdbcClient.sql("INSERT INTO users (id) VALUES ('user-2')").update();
        insertLedger("ledger-1", "user-1");
        insertLedger("ledger-2", "user-2");
        insertInstrument("instrument-1", "ledger-1", "PETR4");
    }

    @Test
    void findById_ownLedger_returnsInstrument() {
        var result = repository.findById("ledger-1", "instrument-1");

        assertThat(result).isPresent();
        assertThat(result.get().symbol()).isEqualTo("PETR4");
    }

    @Test
    void findById_otherLedger_returnsEmpty() {
        var result = repository.findById("ledger-2", "instrument-1");

        assertThat(result).isEmpty();
    }

    @Test
    void deleteById_ownLedger_deletesRow() {
        var deleted = repository.deleteById("ledger-1", "instrument-1");

        assertThat(deleted).isTrue();
        assertThat(countInstruments()).isZero();
    }

    @Test
    void deleteById_otherLedger_leavesRow() {
        var deleted = repository.deleteById("ledger-2", "instrument-1");

        assertThat(deleted).isFalse();
        assertThat(countInstruments()).isEqualTo(1);
    }

    @Test
    void update_scopedToOwnLedger() {
        var hijacked = new Instrument("instrument-1", "ledger-2", "HACK", "Hijacked", InstrumentType.STOCK,
                "USD", null, null, InstrumentStatus.ACTIVE, Instant.now());

        repository.update(hijacked);

        var symbol = jdbcClient.sql("SELECT symbol FROM instruments WHERE id = 'instrument-1'")
                .query(String.class)
                .single();
        assertThat(symbol).isEqualTo("PETR4");
    }

    private long countInstruments() {
        return jdbcClient.sql("SELECT COUNT(*) FROM instruments").query(Long.class).single();
    }

    private void insertLedger(String id, String userId) {
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, :name, NULL, 'BRL', :createdAt)
                        """)
                .param("id", id)
                .param("userId", userId)
                .param("slug", id)
                .param("name", id)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }

    private void insertInstrument(String id, String ledgerId, String symbol) {
        jdbcClient.sql("""
                        INSERT INTO instruments (id, ledger_id, symbol, name, type, currency, market, isin, status, created_at)
                        VALUES (:id, :ledgerId, :symbol, :symbol, 'STOCK', 'BRL', NULL, NULL, 'ACTIVE', :createdAt)
                        """)
                .param("id", id)
                .param("ledgerId", ledgerId)
                .param("symbol", symbol)
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }
}
