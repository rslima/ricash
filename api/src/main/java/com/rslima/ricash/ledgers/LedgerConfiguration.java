package com.rslima.ricash.ledgers;

import com.rslima.ricash.ledgers.accounts.AccountJdbcRepository;
import com.rslima.ricash.ledgers.accounts.AccountRepository;
import com.rslima.ricash.ledgers.accounts.AccountService;
import com.rslima.ricash.ledgers.accounts.AccountServiceBean;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAccountMappingJdbcRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAccountMappingRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationJdbcRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeJdbcRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeService;
import com.rslima.ricash.ledgers.envelopes.EnvelopeServiceBean;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateJdbcRepository;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateRepository;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateService;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateServiceBean;
import com.rslima.ricash.ledgers.exchangerates.ExternalExchangeRateService;
import com.rslima.ricash.ledgers.instruments.InstrumentJdbcRepository;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceJdbcRepository;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceRepository;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceService;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceServiceBean;
import com.rslima.ricash.ledgers.instruments.InstrumentRepository;
import com.rslima.ricash.ledgers.instruments.InstrumentService;
import com.rslima.ricash.ledgers.instruments.InstrumentServiceBean;
import com.rslima.ricash.ledgers.instruments.PortfolioService;
import com.rslima.ricash.ledgers.instruments.PortfolioServiceBean;
import com.rslima.ricash.ledgers.transactions.TransactionJdbcRepository;
import com.rslima.ricash.ledgers.transactions.TransactionRepository;
import com.rslima.ricash.ledgers.transactions.TransactionService;
import com.rslima.ricash.ledgers.transactions.TransactionServiceBean;
import com.rslima.ricash.users.UserRepository;
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
    public LedgerService ledgerService(LedgerRepository ledgerRepository, SlugService slugService, UserRepository userRepository) {
        return new LedgerServiceBean(ledgerRepository, slugService, userRepository);
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
