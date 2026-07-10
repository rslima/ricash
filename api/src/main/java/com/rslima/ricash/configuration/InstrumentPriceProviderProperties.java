package com.rslima.ricash.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External instrument-price provider endpoints (Yahoo Finance's unofficial API).
 * Defaults point at the free query1.finance.yahoo.com endpoints; override in
 * configuration to swap hosts or point tests at a stub. Yahoo rejects default
 * Java user agents, so requests must be sent with a browser-like one.
 *
 * <p>Two plain properties under the same prefix are read elsewhere:
 * {@code ricash.instrument-prices.refresh-enabled} gates the scheduled daily
 * refresh (see {@code SchedulingConfiguration}) and
 * {@code ricash.instrument-prices.refresh-cron} sets its schedule.
 */
@ConfigurationProperties("ricash.instrument-prices")
public record InstrumentPriceProviderProperties(
        String searchBaseUrl,
        String chartBaseUrl,
        String userAgent,
        Long refreshThrottleMillis
) {

    public InstrumentPriceProviderProperties {
        if (searchBaseUrl == null || searchBaseUrl.isBlank()) {
            searchBaseUrl = "https://query1.finance.yahoo.com/v1/finance/search";
        }
        if (chartBaseUrl == null || chartBaseUrl.isBlank()) {
            chartBaseUrl = "https://query1.finance.yahoo.com/v8/finance/chart";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
        }
        if (refreshThrottleMillis == null) {
            refreshThrottleMillis = 500L;
        }
    }
}
