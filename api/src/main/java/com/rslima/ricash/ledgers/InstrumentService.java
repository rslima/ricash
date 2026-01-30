package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing financial instruments.
 */
public interface InstrumentService {

    /**
     * Finds an instrument by ID.
     *
     * @param id the instrument ID
     * @return the instrument if found
     */
    Optional<Instrument> findById(String id);

    /**
     * Finds an instrument by ledger and symbol.
     *
     * @param ledgerId the ledger ID
     * @param symbol the instrument symbol
     * @return the instrument if found
     */
    Optional<Instrument> findByLedgerAndSymbol(String ledgerId, String symbol);

    /**
     * Lists all instruments for a ledger with pagination.
     *
     * @param ledgerId the ledger ID
     * @param pageable pagination information
     * @return page of instruments
     */
    Page<Instrument> listByLedger(String ledgerId, Pageable pageable);

    /**
     * Lists all instruments for a ledger.
     *
     * @param ledgerId the ledger ID
     * @return list of instruments
     */
    List<Instrument> listAllByLedger(String ledgerId);

    /**
     * Creates a new instrument.
     *
     * @param ledgerId the ledger ID
     * @param symbol the ticker symbol
     * @param name the full name
     * @param type the instrument type
     * @param currency the currency
     * @param market the market (optional)
     * @param isin the ISIN code (optional)
     * @return the created instrument
     */
    Instrument create(String ledgerId, String symbol, String name, InstrumentType type,
                      String currency, String market, String isin);

    /**
     * Updates an existing instrument.
     *
     * @param id the instrument ID
     * @param symbol the new symbol
     * @param name the new name
     * @param type the new type
     * @param currency the new currency
     * @param market the new market
     * @param isin the new ISIN
     * @param status the new status
     * @return the updated instrument
     */
    Instrument update(String id, String symbol, String name, InstrumentType type,
                      String currency, String market, String isin, InstrumentStatus status);

    /**
     * Deletes an instrument.
     *
     * @param id the instrument ID
     */
    void delete(String id);
}
