package com.rslima.ricash.ledgers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeRateMapperTest {

    private final ExchangeRateMapper mapper = Mappers.getMapper(ExchangeRateMapper.class);

    @Test
    void toResource_mapsAllFields() {
        var now = Instant.now();
        var date = LocalDate.of(2026, 1, 15);
        var rate = new ExchangeRate("rate-id", "USD", "BRL", new BigDecimal("5.50"), date, "MANUAL", now);

        var result = mapper.toResource(rate);

        assertThat(result.getId()).isEqualTo("rate-id");
        assertThat(result.getFromCurrency()).isEqualTo("USD");
        assertThat(result.getToCurrency()).isEqualTo("BRL");
        assertThat(result.getRate()).isEqualByComparingTo(new BigDecimal("5.50"));
        assertThat(result.getEffectiveDate()).isEqualTo(date);
        assertThat(result.getSource()).isEqualTo("MANUAL");
        assertThat(result.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toResource_mapsExternalSource() {
        var rate = new ExchangeRate("rate-id", "EUR", "USD", new BigDecimal("1.08"), LocalDate.now(), "EXTERNAL_API", Instant.now());

        var result = mapper.toResource(rate);

        assertThat(result.getSource()).isEqualTo("EXTERNAL_API");
    }

    @Test
    void toResource_mapsHighPrecisionRate() {
        var rate = new ExchangeRate("rate-id", "USD", "JPY", new BigDecimal("149.123456"), LocalDate.now(), "BCB", Instant.now());

        var result = mapper.toResource(rate);

        assertThat(result.getRate()).isEqualByComparingTo(new BigDecimal("149.123456"));
    }
}
