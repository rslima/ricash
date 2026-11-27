package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LedgerRepository {
    Page<Ledger> listUserLedgers(String userId, Pageable pageRequest);

    /** The full ledger with its account forest and balances. */
    Optional<Ledger> findBySlug(String userId, String slug);

    /** Single-row lookup for ownership checks; no accounts, no balances. */
    Optional<LedgerRef> findRefBySlug(String userId, String slug);

    Ledger create(Ledger ledger);

    Ledger update(String userId, String slug, String name, String description);

    boolean existsBySlug(String userId, String slug);
}
