package com.rslima.ricash.ledgers.exchangerates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Service for fetching exchange rates from external APIs.
 * Supports multiple providers with fallback logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalExchangeRateService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int RATE_SCALE = 6;

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
                log.info("Found rate from BCB API: {} {} -> {} = {}",
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
        try {
            String bcbCurrency = null;
            boolean inverse = false;

            // Determine which BCB currency code to use
            if ("BRL".equals(toCurrency) && "USD".equals(fromCurrency)) {
                bcbCurrency = "1"; // USD/BRL (Dólar Americano)
            } else if ("BRL".equals(toCurrency) && "EUR".equals(fromCurrency)) {
                bcbCurrency = "21933"; // EUR/BRL (Euro)
            } else if ("BRL".equals(fromCurrency) && "USD".equals(toCurrency)) {
                bcbCurrency = "1";
                inverse = true;
            } else if ("BRL".equals(fromCurrency) && "EUR".equals(toCurrency)) {
                bcbCurrency = "21933";
                inverse = true;
            } else {
                // BCB only supports USD and EUR
                return Optional.empty();
            }

            // Format date as DD-MM-YYYY
            String dateStr = String.format("%02d-%02d-%d",
                date.getDayOfMonth(), date.getMonthValue(), date.getYear());

            // API URL: https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/
            // CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)?@moeda='USD'&@dataCotacao='01-30-2025'
            String url = String.format(
                "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/" +
                "CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)?" +
                "@moeda='%s'&@dataCotacao='%s'&$format=json",
                bcbCurrency.equals("1") ? "USD" : "EUR",
                dateStr
            );

            String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

            if (response == null || response.isEmpty()) {
                log.debug("Empty response from BCB API");
                return Optional.empty();
            }

            // Parse JSON response
            JsonNode root = objectMapper.readTree(response);
            JsonNode value = root.path("value");

            if (value.isEmpty()) {
                log.debug("No data in BCB API response for date {}", dateStr);
                return Optional.empty();
            }

            // Get the first (most recent) rate
            JsonNode firstRate = value.get(0);
            BigDecimal rate = firstRate.path("cotacaoVenda").decimalValue();

            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Invalid rate from BCB API: {}", rate);
                return Optional.empty();
            }

            // If we need the inverse rate (BRL -> other currency)
            if (inverse) {
                rate = BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_UP);
            }

            return Optional.of(rate.setScale(RATE_SCALE, RoundingMode.HALF_UP));

        } catch (Exception e) {
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
        try {
            // Using the free endpoint (no API key required)
            // https://open.er-api.com/v6/latest/USD
            String url = String.format(
                "https://open.er-api.com/v6/latest/%s",
                fromCurrency
            );

            String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

            if (response == null || response.isEmpty()) {
                log.debug("Empty response from ExchangeRate-API");
                return Optional.empty();
            }

            // Parse JSON response
            JsonNode root = objectMapper.readTree(response);

            // Check if request was successful
            String result = root.path("result").asText();
            if (!"success".equals(result)) {
                log.warn("ExchangeRate-API returned error: {}", root.path("error-type").asText());
                return Optional.empty();
            }

            // Get the rate for target currency
            JsonNode rates = root.path("rates");
            if (!rates.has(toCurrency)) {
                log.debug("Currency {} not found in ExchangeRate-API response", toCurrency);
                return Optional.empty();
            }

            BigDecimal rate = rates.path(toCurrency).decimalValue();

            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("Invalid rate from ExchangeRate-API: {}", rate);
                return Optional.empty();
            }

            return Optional.of(rate.setScale(RATE_SCALE, RoundingMode.HALF_UP));

        } catch (Exception e) {
            log.warn("Failed to fetch rate from ExchangeRate-API: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
