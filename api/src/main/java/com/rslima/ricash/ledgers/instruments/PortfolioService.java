package com.rslima.ricash.ledgers.instruments;

import java.util.List;

/**
 * Service for calculating portfolio positions and values, scoped to the
 * caller's ledger.
 */
public interface PortfolioService {

    /** Calculates all instrument positions for an account in the caller's ledger. */
    List<InstrumentPosition> getPositions(String userId, String ledgerSlug, String accountId);

    /** Calculates all instrument positions for the caller's ledger. */
    List<InstrumentPosition> getAllPositions(String userId, String ledgerSlug);
}
