package com.rslima.ricash.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External exchange-rate provider endpoints. Defaults point at the free BCB
 * (Banco Central do Brasil PTAX) and open.er-api.com endpoints; override in
 * configuration to swap providers or point tests at a stub.
 */
@ConfigurationProperties("ricash.exchange-rates")
public record ExchangeRateProviderProperties(String bcbBaseUrl, String erApiBaseUrl) {

    public ExchangeRateProviderProperties {
        if (bcbBaseUrl == null || bcbBaseUrl.isBlank()) {
            bcbBaseUrl = "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata";
        }
        if (erApiBaseUrl == null || erApiBaseUrl.isBlank()) {
            erApiBaseUrl = "https://open.er-api.com/v6/latest";
        }
    }
}
