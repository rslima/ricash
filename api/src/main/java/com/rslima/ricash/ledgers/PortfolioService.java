package com.rslima.ricash.ledgers;

import java.util.List;

/**
 * Service for calculating portfolio positions and values.
 */
public interface PortfolioService {

    /**
     * Calculates all instrument positions for an account.
     *
     * @param ledgerId the ledger ID
     * @param accountId the account ID
     * @return list of positions
     */
    List<InstrumentPosition> getPositions(String ledgerId, String accountId);

    /**
     * Calculates all instrument positions for an entire ledger.
     *
     * @param ledgerId the ledger ID
     * @return list of positions
     */
    List<InstrumentPosition> getAllPositions(String ledgerId);
}
