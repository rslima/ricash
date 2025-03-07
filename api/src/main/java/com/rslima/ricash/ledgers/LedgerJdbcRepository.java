package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;

@RequiredArgsConstructor
@Slf4j
public class LedgerJdbcRepository implements LedgerRepository {
    private final JdbcClient jdbcClient;
}
