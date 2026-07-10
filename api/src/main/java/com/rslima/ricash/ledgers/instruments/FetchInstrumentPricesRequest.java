package com.rslima.ricash.ledgers.instruments;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request to fetch prices for an instrument from the external provider by its
 * ISIN. A null {@code from} fetches just the latest price; otherwise the EOD
 * series is backfilled from that date.
 */
public record FetchInstrumentPricesRequest(
        @NotBlank String instrumentId,
        LocalDate from
) {
}
