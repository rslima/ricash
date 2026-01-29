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
    public AccountService accountService(AccountRepository accountRepository, LedgerRepository ledgerRepository, SlugService slugService) {
        return new AccountServiceBean(accountRepository, ledgerRepository, slugService);
    }

    @Bean
    public AccountRepository accountRepository(JdbcClient jdbcClient) {
        return new AccountJdbcRepository(jdbcClient);
    }

    @Bean
    public TransactionService transactionService(TransactionRepository transactionRepository, LedgerRepository ledgerRepository) {
        return new TransactionServiceBean(transactionRepository, ledgerRepository);
    }

    @Bean
    public TransactionRepository transactionRepository(JdbcClient jdbcClient) {
        return new TransactionJdbcRepository(jdbcClient);
    }
}
