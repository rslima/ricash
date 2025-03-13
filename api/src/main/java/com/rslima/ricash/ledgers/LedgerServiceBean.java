package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class LedgerServiceBean implements LedgerService {
    private final LedgerRepository ledgerRepository;

    @Override
    public Page<Ledger> listUserLedgers(String userId, PageRequest pageRequest) {
        return ledgerRepository.listUserLedgers(userId, pageRequest);
    }

    @Override
    public Optional<Ledger> find(String userId, String id) {
        return ledgerRepository.findById(userId, id);
    }
}
