package com.rslima.ricash.ledgers.instruments;

import java.time.Instant;

/**
 * Represents a financial instrument (stock, ETF, bond, etc.) that can be tracked.
 *
 * @param id unique identifier
 * @param ledgerId the ledger this instrument belongs to
 * @param symbol ticker symbol or identifier (e.g., "PETR4", "AAPL", "Tesouro IPCA+ 2035")
 * @param name full name of the instrument (e.g., "Petrobras PN")
 * @param type the type of instrument
 * @param currency the currency the instrument is quoted in
 * @param market the market where the instrument is traded (e.g., "B3", "NYSE", "TESOURO_DIRETO")
 * @param isin optional ISIN code
 * @param status whether the instrument is active or inactive
 * @param createdAt when this instrument was created
 */
public record Instrument(
    String id,
    String ledgerId,
    String symbol,
    String name,
    InstrumentType type,
    String currency,
    String market,
    String isin,
    InstrumentStatus status,
    Instant createdAt
) {
}
