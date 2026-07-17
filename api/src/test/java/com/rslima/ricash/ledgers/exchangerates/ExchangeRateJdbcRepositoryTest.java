package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRicashApplication.class)
class ExchangeRateJdbcRepositoryTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @BeforeEach
    void setUp() {
        // Exchange rates are global rows, deliberately left alone by
        // DbFixtures.cleanAll; this suite owns the whole table.
        jdbcClient.sql("DELETE FROM exchange_rates").update();
    }

    private ExchangeRate rate(String from, String to, String rate, LocalDate date, String source) {
        return new ExchangeRate(null, from, to, new BigDecimal(rate), date, source, Instant.now());
    }

    @Test
    void findDistinctExternalPairs_collapsesDatesAndOrdersByPair() {
        exchangeRateRepository.save(rate("USD", "BRL", "5.70", LocalDate.of(2026, 7, 14), "EXTERNAL_API"));
        exchangeRateRepository.save(rate("USD", "BRL", "5.75", LocalDate.of(2026, 7, 15), "EXTERNAL_API"));
        exchangeRateRepository.save(rate("EUR", "BRL", "6.10", LocalDate.of(2026, 7, 15), "EXTERNAL_API"));
        exchangeRateRepository.save(rate("BRL", "USD", "0.17", LocalDate.of(2026, 7, 15), "EXTERNAL_API"));

        assertThat(exchangeRateRepository.findDistinctExternalPairs()).containsExactly(
                new CurrencyPair("BRL", "USD"),
                new CurrencyPair("EUR", "BRL"),
                new CurrencyPair("USD", "BRL"));
    }

    @Test
    void findDistinctExternalPairs_ignoresManualOnlyPairs() {
        // A pair the user only ever maintained by hand stays user-managed.
        exchangeRateRepository.save(rate("GBP", "BRL", "7.00", LocalDate.of(2026, 7, 15), "MANUAL"));
        // A manual override on an externally served pair does not unenroll it.
        exchangeRateRepository.save(rate("USD", "BRL", "5.70", LocalDate.of(2026, 7, 14), "EXTERNAL_API"));
        exchangeRateRepository.save(rate("USD", "BRL", "5.60", LocalDate.of(2026, 7, 15), "MANUAL"));

        assertThat(exchangeRateRepository.findDistinctExternalPairs()).containsExactly(
                new CurrencyPair("USD", "BRL"));
    }

    @Test
    void findDistinctExternalPairs_emptyTable_returnsEmptyList() {
        assertThat(exchangeRateRepository.findDistinctExternalPairs()).isEmpty();
    }
}
