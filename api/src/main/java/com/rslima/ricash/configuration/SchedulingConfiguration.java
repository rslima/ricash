package com.rslima.ricash.configuration;

import com.rslima.ricash.ledgers.instruments.InstrumentPriceRefreshScheduler;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling infrastructure for the daily instrument-price refresh — the app's
 * only scheduled task, so the whole configuration is gated by
 * {@code ricash.instrument-prices.refresh-enabled} (default on; tests turn it
 * off in src/test/resources/application.properties).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ricash.instrument-prices", name = "refresh-enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {

    @Bean
    public InstrumentPriceRefreshScheduler instrumentPriceRefreshScheduler(
            InstrumentPriceService instrumentPriceService) {
        return new InstrumentPriceRefreshScheduler(instrumentPriceService);
    }
}
