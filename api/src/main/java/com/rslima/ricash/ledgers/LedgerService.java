package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface LedgerService {
    Page<Ledger> listUserLedgers(String userId, PageRequest pageRequest);
    Optional<Ledger> findBySlug(String userId, String slug);

    /**
     * Resolves the id of a ledger owned by the given user without loading the
     * account tree.
     *
     * @throws LedgerNotFoundException if the slug does not exist for this user
     */
    String requireLedgerId(String userId, String slug);

    Ledger create(String userId, CreateLedgerRequest request);
    Ledger update(String userId, String slug, UpdateLedgerRequest request);
}
