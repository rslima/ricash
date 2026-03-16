package com.rslima.ricash.ledgers.instruments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceJdbcRepository implements InstrumentPriceRepository {

    private final JdbcClient jdbcClient;

    @Override
    public Optional<InstrumentPrice> findById(String id) {
        log.debug("Finding price by id {}", id);

        return jdbcClient.sql("""
                SELECT id, instrument_id, price, effective_date, source, created_at
                FROM instrument_prices
                WHERE id = :id
                """)
            .param("id", id)
            .query(this::mapRow)
            .optional();
    }

    @Override
    public Optional<InstrumentPrice> findPrice(String instrumentId, LocalDate date) {
        log.debug("Finding price for instrument {} on date {}", instrumentId, date);

        return jdbcClient.sql("""
                SELECT id, instrument_id, price, effective_date, source, created_at
                FROM instrument_prices
                WHERE instrument_id = :instrumentId
                  AND effective_date <= :date
                ORDER BY effective_date DESC
                LIMIT 1
                """)
            .param("instrumentId", instrumentId)
            .param("date", Date.valueOf(date))
            .query(this::mapRow)
            .optional();
    }

    @Override
    public Optional<InstrumentPrice> findLatestPrice(String instrumentId) {
        log.debug("Finding latest price for instrument {}", instrumentId);

        return jdbcClient.sql("""
                SELECT id, instrument_id, price, effective_date, source, created_at
                FROM instrument_prices
                WHERE instrument_id = :instrumentId
                ORDER BY effective_date DESC
                LIMIT 1
                """)
            .param("instrumentId", instrumentId)
            .query(this::mapRow)
            .optional();
    }

    @Override
    public Page<InstrumentPrice> findByInstrumentId(String instrumentId, Pageable pageable) {
        log.debug("Finding prices for instrument {} with pagination", instrumentId);

        Long total = jdbcClient.sql("SELECT COUNT(*) FROM instrument_prices WHERE instrument_id = :instrumentId")
            .param("instrumentId", instrumentId)
            .query(Long.class)
            .single();

        List<InstrumentPrice> prices = jdbcClient.sql("""
                SELECT id, instrument_id, price, effective_date, source, created_at
                FROM instrument_prices
                WHERE instrument_id = :instrumentId
                ORDER BY effective_date DESC
                LIMIT :limit OFFSET :offset
                """)
            .param("instrumentId", instrumentId)
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(this::mapRow)
            .list();

        return new PageImpl<>(prices, pageable, total);
    }

    @Override
    public Page<InstrumentPrice> findByLedgerId(String ledgerId, Pageable pageable) {
        log.debug("Finding prices for ledger {} with pagination", ledgerId);

        Long total = jdbcClient.sql("""
                SELECT COUNT(*) FROM instrument_prices p
                JOIN instruments i ON p.instrument_id = i.id
                WHERE i.ledger_id = :ledgerId
                """)
            .param("ledgerId", ledgerId)
            .query(Long.class)
            .single();

        List<InstrumentPrice> prices = jdbcClient.sql("""
                SELECT p.id, p.instrument_id, p.price, p.effective_date, p.source, p.created_at
                FROM instrument_prices p
                JOIN instruments i ON p.instrument_id = i.id
                WHERE i.ledger_id = :ledgerId
                ORDER BY p.effective_date DESC, i.symbol
                LIMIT :limit OFFSET :offset
                """)
            .param("ledgerId", ledgerId)
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(this::mapRow)
            .list();

        return new PageImpl<>(prices, pageable, total);
    }

    @Override
    public List<InstrumentPrice> findLatestPricesByLedgerId(String ledgerId) {
        log.debug("Finding latest prices for ledger {}", ledgerId);

        return jdbcClient.sql("""
                SELECT DISTINCT ON (p.instrument_id)
                    p.id, p.instrument_id, p.price, p.effective_date, p.source, p.created_at
                FROM instrument_prices p
                JOIN instruments i ON p.instrument_id = i.id
                WHERE i.ledger_id = :ledgerId
                ORDER BY p.instrument_id, p.effective_date DESC
                """)
            .param("ledgerId", ledgerId)
            .query(this::mapRow)
            .list();
    }

    @Override
    public InstrumentPrice save(InstrumentPrice price) {
        log.debug("Saving price for instrument {} on date {}", price.instrumentId(), price.effectiveDate());

        final var id = price.id() != null ? price.id() : UUID.randomUUID().toString();
        final var createdAt = price.createdAt() != null ? price.createdAt() : Instant.now();

        jdbcClient.sql("""
                INSERT INTO instrument_prices (id, instrument_id, price, effective_date, source, created_at)
                VALUES (:id, :instrumentId, :price, :effectiveDate, :source, :createdAt)
                ON CONFLICT (instrument_id, effective_date)
                DO UPDATE SET price = EXCLUDED.price, source = EXCLUDED.source
                """)
            .param("id", id)
            .param("instrumentId", price.instrumentId())
            .param("price", price.price())
            .param("effectiveDate", Date.valueOf(price.effectiveDate()))
            .param("source", price.source())
            .param("createdAt", Timestamp.from(createdAt))
            .update();

        return new InstrumentPrice(
            id,
            price.instrumentId(),
            price.price(),
            price.effectiveDate(),
            price.source(),
            createdAt
        );
    }

    @Override
    public void deleteById(String id) {
        log.debug("Deleting price with id {}", id);
        jdbcClient.sql("DELETE FROM instrument_prices WHERE id = :id")
            .param("id", id)
            .update();
    }

    private InstrumentPrice mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new InstrumentPrice(
            rs.getString("id"),
            rs.getString("instrument_id"),
            rs.getBigDecimal("price"),
            rs.getDate("effective_date").toLocalDate(),
            rs.getString("source"),
            rs.getTimestamp("created_at").toInstant()
        );
    }
}
