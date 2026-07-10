package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.ledgers.MonetaryAmount;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Service for managing currency exchange rates and conversions.
 */
public interface ExchangeRateService {

    /**
     * Gets the exchange rate for converting from one currency to another on a specific date.
     * If no rate is found for the exact pair, attempts to find the inverse rate.
     *
     * @param fromCurrency source currency code
     * @param toCurrency target currency code
     * @param date the date for which to get the rate
     * @return the exchange rate if available
     */
    Optional<BigDecimal> getRate(String fromCurrency, String toCurrency, LocalDate date);

    /**
     * Converts a monetary amount from one currency to another using the rate for the given date.
     *
     * @param amount the amount to convert
     * @param fromCurrency source currency code
     * @param toCurrency target currency code
     * @param date the date for which to use the exchange rate
     * @return the converted amount, or empty if no rate is available
     */
    Optional<MonetaryAmount> convert(MonetaryAmount amount, String toCurrency, LocalDate date);

    /**
     * Saves or updates an exchange rate.
     *
     * @param fromCurrency source currency code
     * @param toCurrency target currency code
     * @param rate the conversion rate
     * @param effectiveDate the date this rate becomes effective
     * @param source the source of this rate (e.g., "API", "MANUAL")
     * @return the saved exchange rate
     */
    ExchangeRate saveRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate, String source);

    /**
     * Saves a user-entered rate (source "MANUAL"), normalizing currency codes
     * and validating the pair and value.
     */
    ExchangeRate saveManualRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate);

    /** Lists all stored rates, newest first. */
    Page<ExchangeRate> listRates(Pageable pageable);

    /** Deletes a stored rate. */
    void deleteRate(String id);

    /**
     * Force-fetches the exchange rate from the external provider, ignoring any
     * value already cached in the database, and persists (upserts) the result.
     * Used by the "fetch from external API" action in the UI.
     *
     * @param fromCurrency source currency code
     * @param toCurrency target currency code
     * @param date the date for which to fetch the rate
     * @return the saved rate, or empty if the external provider had no rate
     */
    Optional<ExchangeRate> refreshRate(String fromCurrency, String toCurrency, LocalDate date);
}
