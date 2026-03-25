package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing instrument prices.
 */
public interface InstrumentPriceService {

    /**
     * Gets the price for an instrument on a specific date.
     * If no exact match, returns the most recent price before that date.
     *
     * @param instrumentId the instrument ID
     * @param date the effective date
     * @return the price if found
     */
    Optional<BigDecimal> getPrice(String instrumentId, LocalDate date);

    /**
     * Gets the latest price for an instrument.
     *
     * @param instrumentId the instrument ID
     * @return the latest price if found
     */
    Optional<BigDecimal> getLatestPrice(String instrumentId);

    /**
     * Lists price history for an instrument with pagination.
     *
     * @param instrumentId the instrument ID
     * @param pageable pagination information
     * @return page of prices
     */
    Page<InstrumentPrice> listByInstrument(String instrumentId, Pageable pageable);

    /**
     * Lists all prices for a ledger's instruments with pagination.
     *
     * @param ledgerId the ledger ID
     * @param pageable pagination information
     * @return page of prices
     */
    Page<InstrumentPrice> listByLedger(String ledgerId, Pageable pageable);

    /**
     * Gets the latest prices for all instruments in a ledger.
     *
     * @param ledgerId the ledger ID
     * @return list of latest prices
     */
    List<InstrumentPrice> getLatestPricesByLedger(String ledgerId);

    /**
     * Saves a price for an instrument.
     *
     * @param instrumentId the instrument ID
     * @param price the price value
     * @param effectiveDate the effective date
     * @param source the source of the price
     * @return the saved price
     */
    InstrumentPrice savePrice(String instrumentId, BigDecimal price, LocalDate effectiveDate, String source);

    /**
     * Deletes a price.
     *
     * @param id the price ID
     */
    void delete(String id);
}
