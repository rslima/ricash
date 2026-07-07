package com.rslima.ricash.ledgers.exchangerates;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Base URLs of the external exchange-rate providers; overridable for tests and
 * self-hosted mirrors.
 */
@ConfigurationProperties(prefix = "ricash.exchange-rates")
public record ExchangeRateProviderProperties(
        String bcbBaseUrl,
        String exchangeRateApiBaseUrl
) {
    public ExchangeRateProviderProperties {
        if (bcbBaseUrl == null) {
            bcbBaseUrl = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata";
        }
        if (exchangeRateApiBaseUrl == null) {
            exchangeRateApiBaseUrl = "https://open.er-api.com/v6";
        }
    }
}
