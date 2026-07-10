package com.rslima.ricash.ledgers.instruments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Repository for managing instrument prices.
 */
public interface InstrumentPriceRepository {

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
     * Deletes a price by ID, only if its instrument belongs to the given ledger.
     *
     * @param ledgerId the ledger the price's instrument must belong to
     * @param id the price ID
     * @return number of rows deleted (0 when the price is absent or foreign)
     */
    int deleteById(String ledgerId, String id);
}
