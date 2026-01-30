package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing financial instruments.
 */
public interface InstrumentRepository {

    /**
     * Finds an instrument by ID.
     *
     * @param id the instrument ID
     * @return the instrument if found
     */
    Optional<Instrument> findById(String id);

    /**
     * Finds an instrument by ledger ID and symbol.
     *
     * @param ledgerId the ledger ID
     * @param symbol the instrument symbol
     * @return the instrument if found
     */
    Optional<Instrument> findByLedgerIdAndSymbol(String ledgerId, String symbol);

    /**
     * Finds all instruments for a ledger with pagination.
     *
     * @param ledgerId the ledger ID
     * @param pageable pagination information
     * @return page of instruments
     */
    Page<Instrument> findByLedgerId(String ledgerId, Pageable pageable);

    /**
     * Finds all instruments for a ledger.
     *
     * @param ledgerId the ledger ID
     * @return list of instruments
     */
    List<Instrument> findAllByLedgerId(String ledgerId);

    /**
     * Saves a new instrument.
     *
     * @param instrument the instrument to save
     * @return the saved instrument
     */
    Instrument save(Instrument instrument);

    /**
     * Updates an existing instrument.
     *
     * @param instrument the instrument to update
     * @return the updated instrument
     */
    Instrument update(Instrument instrument);

    /**
     * Deletes an instrument by ID.
     *
     * @param id the instrument ID
     */
    void deleteById(String id);
}
