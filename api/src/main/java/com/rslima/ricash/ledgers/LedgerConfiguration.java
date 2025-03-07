package com.rslima.ricash.ledgers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

@Configuration
public class LedgerConfiguration {
    @Bean
    public LedgerService ledgerService(LedgerRepository ledgerRepository) {
        return new LedgerServiceBean(ledgerRepository);
    }

    @Bean
    public LedgerRepository ledgerRepository(JdbcClient jdbcClient) {
        return new LedgerJdbcRepository(jdbcClient);
    }
}
