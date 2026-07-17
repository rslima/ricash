package com.rslima.ricash.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External instrument-price provider settings (EODHD, eodhd.com). The base URL
 * defaults to the public API host; override in configuration to point tests at
 * a stub. The API token has no default — application.yml binds it to the
 * {@code EODHD_API_TOKEN} environment variable, and fetches are skipped (with
 * a warning) while it is blank.
 *
 * <p>Two plain properties under the same prefix are read elsewhere:
 * {@code ricash.instrument-prices.refresh-enabled} gates the scheduled daily
 * refresh (see {@code SchedulingConfiguration}) and
 * {@code ricash.instrument-prices.refresh-cron} sets its schedule.
 */
@ConfigurationProperties("ricash.instrument-prices")
public record InstrumentPriceProviderProperties(
        String baseUrl,
        String apiToken,
        Long refreshThrottleMillis
) {

    public InstrumentPriceProviderProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://eodhd.com/api";
        }
        if (apiToken == null) {
            apiToken = "";
        }
        if (refreshThrottleMillis == null) {
            refreshThrottleMillis = 500L;
        }
    }
}
