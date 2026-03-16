package com.rslima.ricash.ledgers.exchangerates;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing exchange rates.
 */
public interface ExchangeRateRepository {

    /**
     * Finds the exchange rate for a specific currency pair on a given date.
     * If no exact date match is found, returns the most recent rate before that date.
     *
     * @param fromCurrency source currency code
     * @param toCurrency target currency code
     * @param date the effective date
     * @return the exchange rate if found
     */
    Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency, LocalDate date);

    /**
     * Saves a new exchange rate.
     *
     * @param exchangeRate the rate to save
     * @return the saved rate
     */
    ExchangeRate save(ExchangeRate exchangeRate);

    /**
     * Finds all exchange rates for a specific date.
     *
     * @param date the effective date
     * @return list of rates
     */
    List<ExchangeRate> findAllByDate(LocalDate date);

    /**
     * Finds the most recent exchange rate for a currency pair.
     *
     * @param fromCurrency source currency code
     * @param toCurrency target currency code
     * @return the most recent rate if found
     */
    Optional<ExchangeRate> findLatestRate(String fromCurrency, String toCurrency);

    /**
     * Finds all exchange rates with pagination.
     *
     * @param pageable pagination information
     * @return page of exchange rates
     */
    Page<ExchangeRate> findAll(Pageable pageable);

    /**
     * Deletes an exchange rate by ID.
     *
     * @param id the exchange rate ID
     */
    void deleteById(String id);
}
