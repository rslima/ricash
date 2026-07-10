package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.configuration.ExchangeRateProviderProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for fetching exchange rates from external APIs.
 * Supports multiple providers with fallback logic.
 */
@RequiredArgsConstructor
@Slf4j
public class ExternalExchangeRateService {

    private final RestClient restClient;
    private final ExchangeRateProviderProperties properties;

    private static final int RATE_SCALE = 6;

    /** BCB PTAX response: {"value":[{"cotacaoVenda": 5.75, ...}]} */
    record BcbResponse(List<BcbQuote> value) {
    }

    record BcbQuote(BigDecimal cotacaoVenda) {
    }

    /** open.er-api.com response: {"result":"success","rates":{"BRL":5.75,...}} */
    record ErApiResponse(String result, Map<String, BigDecimal> rates) {
    }

    /**
     * Fetches exchange rate from external APIs.
     * Tries BCB API first (for BRL rates), then falls back to ExchangeRate-API.
     *
     * @param fromCurrency source currency
     * @param toCurrency target currency
     * @param date the date for the rate (may not be honored by all APIs)
     * @return exchange rate if found
     */
    public Optional<BigDecimal> fetchRate(String fromCurrency, String toCurrency, LocalDate date) {
        log.debug("Fetching external rate from {} to {} for date {}", fromCurrency, toCurrency, date);

        // Try BCB API if either currency is BRL
        if ("BRL".equals(fromCurrency) || "BRL".equals(toCurrency)) {
            Optional<BigDecimal> bcbRate = fetchFromBCB(fromCurrency, toCurrency, date);
            if (bcbRate.isPresent()) {
                log.info("Found rate from BCB API: {} -> {} = {}",
                    fromCurrency, toCurrency, bcbRate.get());
                return bcbRate;
            }
        }

        // Fallback to ExchangeRate-API (uses latest rates, ignores date parameter)
        Optional<BigDecimal> apiRate = fetchFromExchangeRateAPI(fromCurrency, toCurrency);
        if (apiRate.isPresent()) {
            log.info("Found rate from ExchangeRate-API: {} -> {} = {}",
                fromCurrency, toCurrency, apiRate.get());
        }

        return apiRate;
    }

    /**
     * Fetches rate from Banco Central do Brasil API.
     * Supports USD and EUR to/from BRL.
     */
    private Optional<BigDecimal> fetchFromBCB(String fromCurrency, String toCurrency, LocalDate date) {
        final String bcbSymbol;
        final boolean inverse;

        if ("BRL".equals(toCurrency) && ("USD".equals(fromCurrency) || "EUR".equals(fromCurrency))) {
            bcbSymbol = fromCurrency;
            inverse = false;
        } else if ("BRL".equals(fromCurrency) && ("USD".equals(toCurrency) || "EUR".equals(toCurrency))) {
            bcbSymbol = toCurrency;
            inverse = true;
        } else {
            // BCB only quotes USD and EUR against BRL
            return Optional.empty();
        }

        // BCB expects the date as DD-MM-YYYY
        String dateStr = String.format("%02d-%02d-%d",
            date.getDayOfMonth(), date.getMonthValue(), date.getYear());

        String url = String.format(
            "%s/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)?@moeda='%s'&@dataCotacao='%s'&$format=json",
            properties.bcbBaseUrl(), bcbSymbol, dateStr);

        try {
            BcbResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(BcbResponse.class);

            if (response == null || response.value() == null || response.value().isEmpty()) {
                log.debug("No data in BCB API response for date {}", dateStr);
                return Optional.empty();
            }

            BigDecimal rate = response.value().getFirst().cotacaoVenda();
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Invalid rate from BCB API: {}", rate);
                return Optional.empty();
            }

            if (inverse) {
                rate = BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_UP);
            }

            return Optional.of(rate.setScale(RATE_SCALE, RoundingMode.HALF_UP));

        } catch (RestClientException | JacksonException e) {
            log.warn("Failed to fetch rate from BCB API: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches rate from ExchangeRate-API (https://www.exchangerate-api.com).
     * Free tier: 1500 requests/month.
     * Always returns latest rates (doesn't support historical dates).
     */
    private Optional<BigDecimal> fetchFromExchangeRateAPI(String fromCurrency, String toCurrency) {
        String url = String.format("%s/%s", properties.erApiBaseUrl(), fromCurrency);

        try {
            ErApiResponse response = restClient.get()
                .uri(url)
                .retrieve()
                .body(ErApiResponse.class);

            if (response == null || !"success".equals(response.result())) {
                log.warn("ExchangeRate-API returned no success result for {}", fromCurrency);
                return Optional.empty();
            }

            BigDecimal rate = response.rates() != null ? response.rates().get(toCurrency) : null;
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No usable rate for {} in ExchangeRate-API response", toCurrency);
                return Optional.empty();
            }

            return Optional.of(rate.setScale(RATE_SCALE, RoundingMode.HALF_UP));

        } catch (RestClientException | JacksonException e) {
            log.warn("Failed to fetch rate from ExchangeRate-API: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
