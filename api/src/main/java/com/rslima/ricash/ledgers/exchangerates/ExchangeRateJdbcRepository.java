package com.rslima.ricash.ledgers.exchangerates;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class ExchangeRateJdbcRepository implements ExchangeRateRepository {

    private static final RowMapper<ExchangeRate> ROW_MAPPER = (rs, rowNum) -> new ExchangeRate(
        rs.getString("id"),
        rs.getString("from_currency"),
        rs.getString("to_currency"),
        rs.getBigDecimal("rate"),
        rs.getDate("effective_date").toLocalDate(),
        rs.getString("source"),
        rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcClient jdbcClient;

    @Override
    public Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency, LocalDate date) {
        log.debug("Finding exchange rate from {} to {} for date {}", fromCurrency, toCurrency, date);

        return jdbcClient.sql("""
                SELECT id, from_currency, to_currency, rate, effective_date, source, created_at
                FROM exchange_rates
                WHERE from_currency = :fromCurrency
                  AND to_currency = :toCurrency
                  AND effective_date <= :date
                ORDER BY effective_date DESC
                LIMIT 1
                """)
            .param("fromCurrency", fromCurrency)
            .param("toCurrency", toCurrency)
            .param("date", Date.valueOf(date))
            .query(ROW_MAPPER)
            .optional();
    }

    @Override
    public ExchangeRate save(ExchangeRate exchangeRate) {
        log.debug("Saving exchange rate: {} {} -> {} on {}",
            exchangeRate.rate(), exchangeRate.fromCurrency(), exchangeRate.toCurrency(), exchangeRate.effectiveDate());

        final var id = exchangeRate.id() != null ? exchangeRate.id() : UuidCreator.getTimeOrderedEpoch().toString();
        final var createdAt = exchangeRate.createdAt() != null ? exchangeRate.createdAt() : Instant.now();

        jdbcClient.sql("""
                INSERT INTO exchange_rates (id, from_currency, to_currency, rate, effective_date, source, created_at)
                VALUES (:id, :fromCurrency, :toCurrency, :rate, :effectiveDate, :source, :createdAt)
                ON CONFLICT (from_currency, to_currency, effective_date)
                DO UPDATE SET rate = EXCLUDED.rate, source = EXCLUDED.source
                """)
            .param("id", id)
            .param("fromCurrency", exchangeRate.fromCurrency())
            .param("toCurrency", exchangeRate.toCurrency())
            .param("rate", exchangeRate.rate())
            .param("effectiveDate", Date.valueOf(exchangeRate.effectiveDate()))
            .param("source", exchangeRate.source())
            .param("createdAt", Timestamp.from(createdAt))
            .update();

        return new ExchangeRate(
            id,
            exchangeRate.fromCurrency(),
            exchangeRate.toCurrency(),
            exchangeRate.rate(),
            exchangeRate.effectiveDate(),
            exchangeRate.source(),
            createdAt
        );
    }

    @Override
    public Page<ExchangeRate> findAll(Pageable pageable) {
        log.debug("Finding all exchange rates with pagination: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        // Count total
        Long total = jdbcClient.sql("SELECT COUNT(*) FROM exchange_rates")
            .query(Long.class)
            .single();

        // Query with pagination
        List<ExchangeRate> rates = jdbcClient.sql("""
                SELECT id, from_currency, to_currency, rate, effective_date, source, created_at
                FROM exchange_rates
                ORDER BY effective_date DESC, from_currency, to_currency
                LIMIT :limit OFFSET :offset
                """)
            .param("limit", pageable.getPageSize())
            .param("offset", pageable.getOffset())
            .query(ROW_MAPPER)
            .list();

        return new PageImpl<>(rates, pageable, total);
    }

    @Override
    public void deleteById(String id) {
        log.debug("Deleting exchange rate with id {}", id);
        jdbcClient.sql("DELETE FROM exchange_rates WHERE id = :id")
            .param("id", id)
            .update();
    }
}
