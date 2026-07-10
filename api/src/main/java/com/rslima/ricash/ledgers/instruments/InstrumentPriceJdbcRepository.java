package com.rslima.ricash.ledgers.instruments;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceJdbcRepository implements InstrumentPriceRepository {

    private final JdbcClient jdbcClient;

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

        final var id = price.id() != null ? price.id() : UuidCreator.getTimeOrderedEpoch().toString();
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
    public int deleteById(String ledgerId, String id) {
        log.debug("Deleting price with id {} in ledger {}", id, ledgerId);
        return jdbcClient.sql("""
                DELETE FROM instrument_prices p
                USING instruments i
                WHERE p.id = :id AND i.id = p.instrument_id AND i.ledger_id = :ledgerId
                """)
            .param("id", id)
            .param("ledgerId", ledgerId)
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
