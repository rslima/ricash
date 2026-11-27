package com.rslima.ricash.ledgers;

/**
 * Single place where a caller's ledger is resolved by slug. The lookup is
 * scoped by userId, so it doubles as the ownership check: a slug the user does
 * not own is indistinguishable from one that does not exist (404).
 */
public class LedgerAccess {

    private final LedgerRepository ledgerRepository;

    public LedgerAccess(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * Ownership check for every nested-resource call: one single-row query,
     * no account forest or balance rollup.
     */
    public LedgerRef requireLedger(String userId, String ledgerSlug) {
        return ledgerRepository.findRefBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }

    /**
     * Like {@link #requireLedger} but loads the full account forest with
     * balances; only for callers that actually read {@code accounts()}.
     */
    public Ledger requireLedgerWithAccounts(String userId, String ledgerSlug) {
        return ledgerRepository.findBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
