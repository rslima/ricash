package com.rslima.ricash.ledgers.instruments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing instrument prices.
 */
public interface InstrumentPriceRepository {

    /**
     * Finds a price by ID.
     *
     * @param id the price ID
     * @return the price if found
     */
    Optional<InstrumentPrice> findById(String id);

    /**
     * Finds the price for an instrument on a specific date.
     * If no exact match, returns the most recent price before that date.
     *
     * @param instrumentId the instrument ID
     * @param date the effective date
     * @return the price if found
     */
    Optional<InstrumentPrice> findPrice(String instrumentId, LocalDate date);

    /**
     * Finds the latest price for an instrument.
     *
     * @param instrumentId the instrument ID
     * @return the latest price if found
     */
    Optional<InstrumentPrice> findLatestPrice(String instrumentId);

    /**
     * Finds all prices for an instrument with pagination.
     *
     * @param instrumentId the instrument ID
     * @param pageable pagination information
     * @return page of prices
     */
    Page<InstrumentPrice> findByInstrumentId(String instrumentId, Pageable pageable);

    /**
     * Finds all prices for a ledger's instruments with pagination.
     *
     * @param ledgerId the ledger ID
     * @param pageable pagination information
     * @return page of prices
     */
    Page<InstrumentPrice> findByLedgerId(String ledgerId, Pageable pageable);

    /**
     * Finds the latest prices for all instruments in a ledger.
     *
     * @param ledgerId the ledger ID
     * @return list of latest prices
     */
    List<InstrumentPrice> findLatestPricesByLedgerId(String ledgerId);

    /**
     * Saves a new price.
     *
     * @param price the price to save
     * @return the saved price
     */
    InstrumentPrice save(InstrumentPrice price);

    /**
     * Deletes a price by ID.
     *
     * @param id the price ID
     */
    void deleteById(String id);
}
