package com.rslima.ricash.ledgers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
public class LedgerConfiguration {

    // Instrument beans
    @Bean
    public InstrumentService instrumentService(InstrumentRepository instrumentRepository) {
        return new InstrumentServiceBean(instrumentRepository);
    }

    @Bean
    public InstrumentRepository instrumentRepository(JdbcClient jdbcClient) {
        return new InstrumentJdbcRepository(jdbcClient);
    }

    @Bean
    public InstrumentPriceService instrumentPriceService(InstrumentPriceRepository instrumentPriceRepository) {
        return new InstrumentPriceServiceBean(instrumentPriceRepository);
    }

    @Bean
    public InstrumentPriceRepository instrumentPriceRepository(JdbcClient jdbcClient) {
        return new InstrumentPriceJdbcRepository(jdbcClient);
    }

    @Bean
    public PortfolioService portfolioService(
            JdbcClient jdbcClient,
            InstrumentRepository instrumentRepository,
            InstrumentPriceRepository instrumentPriceRepository
    ) {
        return new PortfolioServiceBean(jdbcClient, instrumentRepository, instrumentPriceRepository);
    }

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
    public TransactionService transactionService(
            TransactionRepository transactionRepository,
            LedgerRepository ledgerRepository,
            AccountRepository accountRepository,
            ExchangeRateService exchangeRateService
    ) {
        return new TransactionServiceBean(transactionRepository, ledgerRepository, accountRepository, exchangeRateService);
    }

    @Bean
    public TransactionRepository transactionRepository(JdbcClient jdbcClient) {
        return new TransactionJdbcRepository(jdbcClient);
    }

    @Bean
    public ExchangeRateService exchangeRateService(
            ExchangeRateRepository exchangeRateRepository,
            ExternalExchangeRateService externalExchangeRateService
    ) {
        return new ExchangeRateServiceBean(exchangeRateRepository, externalExchangeRateService);
    }

    @Bean
    public ExchangeRateRepository exchangeRateRepository(JdbcClient jdbcClient) {
        return new ExchangeRateJdbcRepository(jdbcClient);
    }

    // Envelope beans
    @Bean
    public EnvelopeService envelopeService(
            EnvelopeRepository envelopeRepository,
            EnvelopeAllocationRepository allocationRepository,
            EnvelopeAccountMappingRepository mappingRepository,
            LedgerRepository ledgerRepository
    ) {
        return new EnvelopeServiceBean(envelopeRepository, allocationRepository, mappingRepository, ledgerRepository);
    }

    @Bean
    public EnvelopeRepository envelopeRepository(JdbcClient jdbcClient) {
        return new EnvelopeJdbcRepository(jdbcClient);
    }

    @Bean
    public EnvelopeAllocationRepository envelopeAllocationRepository(JdbcClient jdbcClient) {
        return new EnvelopeAllocationJdbcRepository(jdbcClient);
    }

    @Bean
    public EnvelopeAccountMappingRepository envelopeAccountMappingRepository(JdbcClient jdbcClient) {
        return new EnvelopeAccountMappingJdbcRepository(jdbcClient);
    }
}
