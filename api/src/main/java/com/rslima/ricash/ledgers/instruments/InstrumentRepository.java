package com.rslima.ricash.ledgers.instruments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing financial instruments.
 */
public interface InstrumentRepository {

    /**
     * Finds an instrument by ID within a ledger.
     *
     * @param ledgerId the ledger the instrument must belong to
     * @param id the instrument ID
     * @return the instrument if found in that ledger
     */
    Optional<Instrument> findById(String ledgerId, String id);

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
     * Finds every ACTIVE instrument with a non-blank ISIN across ALL ledgers.
     * Deliberately not ledger-scoped: only for the scheduled price refresh,
     * which runs in a system context. Never expose through caller-facing paths.
     *
     * @return active instruments with an ISIN, across every ledger
     */
    List<Instrument> findAllActiveWithIsinSystemWide();

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
     * Deletes an instrument by ID within a ledger.
     *
     * @param ledgerId the ledger the instrument must belong to
     * @param id the instrument ID
     */
    void deleteById(String ledgerId, String id);
}
