package com.rslima.ricash.ledgers;

import java.util.List;
import java.util.Optional;

public interface LedgerRepository {
    List<Ledger> listAll(String userId);

    Optional<Ledger> findById(String userId, String id);
}
