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

    public Ledger requireLedger(String userId, String ledgerSlug) {
        return ledgerRepository.findBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
