package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface LedgerService {
    Page<Ledger> listUserLedgers(String userId, PageRequest pageRequest);
    Optional<Ledger> find(String userId, String id);
}
