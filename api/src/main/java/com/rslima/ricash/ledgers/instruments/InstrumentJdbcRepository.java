package com.rslima.ricash.ledgers.instruments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class InstrumentJdbcRepository implements InstrumentRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<Instrument> findById(String id) {
        log.debug("Finding instrument by id {}", id);

        return jdbcClient.sql("""
                SELECT id, ledger_id, symbol, name, type, currency, market, isin, status, created_at
                FROM instruments
                WHERE id = :id
                """)
            .param("id", id)
            .query(this::mapRow)
            .optional();
    }

    @Override
    public Optional<Instrument> findByLedgerIdAndSymbol(String ledgerId, String symbol) {
        log.debug("Finding instrument by ledger {} and symbol {}", ledgerId, symbol);

        return jdbcClient.sql("""
                SELECT id, ledger_id, symbol, name, type, currency, market, isin, status, created_at
                FROM instruments
                WHERE ledger_id = :ledgerId AND symbol = :symbol
                """)
            .param("ledgerId", ledgerId)
            .param("symbol", symbol)
            .query(this::mapRow)
            .optional();
    }

    @Override
    public Page<Instrument> findByLedgerId(String ledgerId, Pageable pageable) {
        log.debug("Finding instruments for ledger {} with pagination", ledgerId);

        Long total = jdbcClient.sql("SELECT COUNT(*) FROM instruments WHERE ledger_id = :ledgerId")
            .param("ledgerId", ledgerId)
            .query(Long.class)
            .single();

        List<Instrument> instruments = jdbcClient.sql("""
                SELECT id, ledger_id, symbol, name, type, currency, market, isin, status, created_at
                FROM instruments
                WHERE ledger_id = :ledgerId
                ORDER BY symbol
                LIMIT :limit OFFSET :offset
                """)
            .param("ledgerId", ledgerId)
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(this::mapRow)
            .list();

        return new PageImpl<>(instruments, pageable, total);
    }

    @Override
    public List<Instrument> findAllByLedgerId(String ledgerId) {
        log.debug("Finding all instruments for ledger {}", ledgerId);

        return jdbcClient.sql("""
                SELECT id, ledger_id, symbol, name, type, currency, market, isin, status, created_at
                FROM instruments
                WHERE ledger_id = :ledgerId
                ORDER BY symbol
                """)
            .param("ledgerId", ledgerId)
            .query(this::mapRow)
            .list();
    }

    @Override
    public Instrument save(Instrument instrument) {
        log.debug("Saving instrument: {}", instrument.symbol());

        final var id = instrument.id() != null ? instrument.id() : UUID.randomUUID().toString();
        final var createdAt = instrument.createdAt() != null ? instrument.createdAt() : Instant.now();

        jdbcClient.sql("""
                INSERT INTO instruments (id, ledger_id, symbol, name, type, currency, market, isin, status, created_at)
                VALUES (:id, :ledgerId, :symbol, :name, :type, :currency, :market, :isin, :status, :createdAt)
                """)
            .param("id", id)
            .param("ledgerId", instrument.ledgerId())
            .param("symbol", instrument.symbol())
            .param("name", instrument.name())
            .param("type", instrument.type().name())
            .param("currency", instrument.currency())
            .param("market", instrument.market())
            .param("isin", instrument.isin())
            .param("status", instrument.status().name())
            .param("createdAt", Timestamp.from(createdAt))
            .update();

        return new Instrument(
            id,
            instrument.ledgerId(),
            instrument.symbol(),
            instrument.name(),
            instrument.type(),
            instrument.currency(),
            instrument.market(),
            instrument.isin(),
            instrument.status(),
            createdAt
        );
    }

    @Override
    public Instrument update(Instrument instrument) {
        log.debug("Updating instrument: {}", instrument.id());

        jdbcClient.sql("""
                UPDATE instruments
                SET symbol = :symbol, name = :name, type = :type, currency = :currency,
                    market = :market, isin = :isin, status = :status
                WHERE id = :id
                """)
            .param("id", instrument.id())
            .param("symbol", instrument.symbol())
            .param("name", instrument.name())
            .param("type", instrument.type().name())
            .param("currency", instrument.currency())
            .param("market", instrument.market())
            .param("isin", instrument.isin())
            .param("status", instrument.status().name())
            .update();

        return instrument;
    }

    @Override
    public void deleteById(String id) {
        log.debug("Deleting instrument with id {}", id);
        jdbcClient.sql("DELETE FROM instruments WHERE id = :id")
            .param("id", id)
            .update();
    }

    private Instrument mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Instrument(
            rs.getString("id"),
            rs.getString("ledger_id"),
            rs.getString("symbol"),
            rs.getString("name"),
            InstrumentType.valueOf(rs.getString("type")),
            rs.getString("currency"),
            rs.getString("market"),
            rs.getString("isin"),
            InstrumentStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
