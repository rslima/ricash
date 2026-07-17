package com.rslima.ricash.configuration;

import com.rslima.ricash.ledgers.exchangerates.ExchangeRateRefreshScheduler;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateService;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceRefreshScheduler;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling infrastructure for the daily refreshes — instrument prices and
 * exchange rates. Each scheduler is gated by its own property (default on;
 * tests turn both off in src/test/resources/application.properties):
 * {@code ricash.instrument-prices.refresh-enabled} and
 * {@code ricash.exchange-rates.refresh-enabled}. This is the app's only
 * {@code @EnableScheduling}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ricash.instrument-prices", name = "refresh-enabled",
            havingValue = "true", matchIfMissing = true)
    public InstrumentPriceRefreshScheduler instrumentPriceRefreshScheduler(
            InstrumentPriceService instrumentPriceService) {
        return new InstrumentPriceRefreshScheduler(instrumentPriceService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ricash.exchange-rates", name = "refresh-enabled",
            havingValue = "true", matchIfMissing = true)
    public ExchangeRateRefreshScheduler exchangeRateRefreshScheduler(
            ExchangeRateService exchangeRateService) {
        return new ExchangeRateRefreshScheduler(exchangeRateService);
    }
}
