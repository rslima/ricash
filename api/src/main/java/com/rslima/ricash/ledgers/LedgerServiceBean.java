package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class LedgerServiceBean implements LedgerService {
    private final LedgerRepository ledgerRepository;

    @Override
    public List<Ledger> list() {
        return List.of();
    }

    @Override
    public Optional<Ledger> find(String id) {
        return Optional.empty();
    }
}
