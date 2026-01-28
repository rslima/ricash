package com.rslima.ricash.ledgers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class LedgerConfiguration {
    @Bean
    public LedgerService ledgerService(LedgerRepository ledgerRepository, SlugService slugService) {
        return new LedgerServiceBean(ledgerRepository, slugService);
    }

    @Bean
    public LedgerRepository ledgerRepository(JdbcClient jdbcClient) {
        return new LedgerJdbcRepository(jdbcClient);
    }

    @Bean
    public AccountService accountService(AccountRepository accountRepository, LedgerRepository ledgerRepository) {
        return new AccountServiceBean(accountRepository, ledgerRepository);
    }

    @Bean
    public AccountRepository accountRepository(JdbcClient jdbcClient) {
        return new AccountJdbcRepository(jdbcClient);
    }
}
