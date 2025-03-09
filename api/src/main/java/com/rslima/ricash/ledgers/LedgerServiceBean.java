package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class LedgerServiceBean implements LedgerService {
    private final LedgerRepository ledgerRepository;

    @Override
    public List<Ledger> list(String userId) {
        return ledgerRepository.listAll(userId);
    }

    @Override
    public Optional<Ledger> find(String userId, String id) {
        return ledgerRepository.findById(userId, id);
    }
}
