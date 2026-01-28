package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

public interface LedgerRepository {
    Page<Ledger> listUserLedgers(String userId, PageRequest pageRequest);

    Optional<Ledger> findById(String userId, String id);

    Ledger create(Ledger ledger);
}
