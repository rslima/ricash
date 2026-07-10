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

    /**
     * Fetches prices from the external provider (by the instrument's ISIN) for
     * an instrument in the caller's ledger and upserts them: the latest price,
     * plus the EOD series since {@code from} when it is non-null.
     *
     * @return the latest saved price
     */
    InstrumentPrice fetchPrices(String userId, String ledgerSlug, String instrumentId, LocalDate from);

    /**
     * System context (scheduled refresh): fetches the latest external price for
     * every ACTIVE instrument with an ISIN, across all ledgers. Failures for one
     * instrument are logged and skipped.
     *
     * @return the number of instruments whose prices were refreshed
     */
    int refreshAllActivePrices();

    /** Deletes a price belonging to the caller's ledger. */
    void delete(String userId, String ledgerSlug, String priceId);
}
