package com.rslima.ricash.ledgers;

import java.util.List;
import java.util.Optional;

public interface LedgerService {
    List<Ledger> list();
    Optional<Ledger> find(String id);
}
