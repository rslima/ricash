package com.rslima.ricash.ledgers.instruments;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-side aggregation of instrument-tagged transaction entries into raw
 * position data (quantities and invested amounts per instrument).
 */
public interface PortfolioRepository {

    record PositionData(
            String instrumentId,
            BigDecimal debitQuantity,
            BigDecimal creditQuantity,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {}

    /** Aggregates positions across all accounts of the ledger. */
    List<PositionData> aggregatePositions(String ledgerId);

    /** Aggregates positions for a single account of the ledger. */
    List<PositionData> aggregatePositions(String ledgerId, String accountId);
}
