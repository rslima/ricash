package com.rslima.ricash.ledgers.instruments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for managing instrument prices. Caller-facing operations take
 * userId + ledgerSlug and enforce that the target instrument/price belongs to
 * the caller's ledger.
 */
public interface InstrumentPriceService {

    /** Lists price history for an instrument in the caller's ledger. */
    Page<InstrumentPrice> listByInstrument(String userId, String ledgerSlug, String instrumentId, Pageable pageable);

    /** Lists all prices for the caller's ledger. */
    Page<InstrumentPrice> listByLedger(String userId, String ledgerSlug, Pageable pageable);

    /** Gets the latest prices for all instruments in a ledger. */
    List<InstrumentPrice> getLatestPricesByLedger(String ledgerId);

    /** Saves a price for an instrument in the caller's ledger. */
    InstrumentPrice savePrice(String userId, String ledgerSlug, String instrumentId,
                              BigDecimal price, LocalDate effectiveDate, String source);

    /** Deletes a price belonging to the caller's ledger. */
    void delete(String userId, String ledgerSlug, String priceId);
}
