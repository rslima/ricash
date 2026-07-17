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
import com.rslima.ricash.ledgers.instruments.PortfolioJdbcRepository;
import com.rslima.ricash.ledgers.instruments.PortfolioRepository;
import com.rslima.ricash.ledgers.instruments.PortfolioService;
import com.rslima.ricash.ledgers.instruments.PortfolioServiceBean;
import com.rslima.ricash.ledgers.instruments.EodhdPriceService;
import com.rslima.ricash.ledgers.transactions.TransactionJdbcRepository;
import com.rslima.ricash.ledgers.transactions.TransactionRepository;
import com.rslima.ricash.ledgers.transactions.TransactionService;
import com.rslima.ricash.ledgers.transactions.TransactionServiceBean;
import com.rslima.ricash.ledgers.transactions.export.TransactionExportService;
import com.rslima.ricash.ledgers.transactions.export.TransactionExportServiceBean;
import com.rslima.ricash.users.UserRepository;
import com.rslima.ricash.configuration.ExchangeRateProviderProperties;
import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class LedgerConfiguration {

    @Bean
    public SlugService slugService() {
        return new SlugService();
    }

    @Bean
    public LedgerAccess ledgerAccess(LedgerRepository ledgerRepository) {
        return new LedgerAccess(ledgerRepository);
    }

    @Bean
    public ExternalExchangeRateService externalExchangeRateService(
            RestClient.Builder restClientBuilder,
            ExchangeRateProviderProperties exchangeRateProviderProperties
    ) {
        // Timeouts come from the auto-configured builder (spring.http.clients.*).
        return new ExternalExchangeRateService(restClientBuilder.build(), exchangeRateProviderProperties);
    }

    // Instrument beans
    @Bean
    public InstrumentService instrumentService(InstrumentRepository instrumentRepository, LedgerAccess ledgerAccess) {
        return new InstrumentServiceBean(instrumentRepository, ledgerAccess);
    }

    @Bean
    public InstrumentRepository instrumentRepository(JdbcClient jdbcClient) {
        return new InstrumentJdbcRepository(jdbcClient);
    }

    @Bean
    public EodhdPriceService eodhdPriceService(
            RestClient.Builder restClientBuilder,
            InstrumentPriceProviderProperties instrumentPriceProviderProperties
    ) {
        // Timeouts come from the auto-configured builder (spring.http.clients.*).
        return new EodhdPriceService(restClientBuilder.build(), instrumentPriceProviderProperties,
                Clock.systemDefaultZone());
    }

    @Bean
    public InstrumentPriceService instrumentPriceService(
            InstrumentPriceRepository instrumentPriceRepository,
            InstrumentRepository instrumentRepository,
            LedgerAccess ledgerAccess,
            EodhdPriceService eodhdPriceService,
            InstrumentPriceProviderProperties instrumentPriceProviderProperties
    ) {
        return new InstrumentPriceServiceBean(instrumentPriceRepository, instrumentRepository, ledgerAccess,
                eodhdPriceService, instrumentPriceProviderProperties);
    }

    @Bean
    public InstrumentPriceRepository instrumentPriceRepository(JdbcClient jdbcClient) {
        return new InstrumentPriceJdbcRepository(jdbcClient);
    }

    @Bean
    public PortfolioRepository portfolioRepository(JdbcClient jdbcClient) {
        return new PortfolioJdbcRepository(jdbcClient);
    }

    @Bean
    public PortfolioService portfolioService(
            PortfolioRepository portfolioRepository,
            InstrumentRepository instrumentRepository,
            InstrumentPriceRepository instrumentPriceRepository,
            LedgerAccess ledgerAccess
    ) {
        return new PortfolioServiceBean(portfolioRepository, instrumentRepository, instrumentPriceRepository, ledgerAccess);
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
    public AccountService accountService(AccountRepository accountRepository, LedgerAccess ledgerAccess, SlugService slugService) {
        return new AccountServiceBean(accountRepository, ledgerAccess, slugService);
    }

    @Bean
    public AccountRepository accountRepository(JdbcClient jdbcClient) {
        return new AccountJdbcRepository(jdbcClient);
    }

    @Bean
    public TransactionService transactionService(
            TransactionRepository transactionRepository,
            LedgerAccess ledgerAccess,
            AccountRepository accountRepository,
            ExchangeRateService exchangeRateService
    ) {
        return new TransactionServiceBean(transactionRepository, ledgerAccess, accountRepository, exchangeRateService);
    }

    @Bean
    public TransactionRepository transactionRepository(JdbcClient jdbcClient) {
        return new TransactionJdbcRepository(jdbcClient);
    }

    @Bean
    public TransactionExportService transactionExportService(
            TransactionRepository transactionRepository,
            LedgerAccess ledgerAccess
    ) {
        return new TransactionExportServiceBean(transactionRepository, ledgerAccess);
    }

    @Bean
    public ExchangeRateService exchangeRateService(
            ExchangeRateRepository exchangeRateRepository,
            ExternalExchangeRateService externalExchangeRateService
    ) {
        // The rate sweep stamps rows with "today" in the zone its cron fires
        // in (see ExchangeRateRefreshScheduler) so PTAX quotes land on the
        // right calendar date whatever the server timezone is.
        return new ExchangeRateServiceBean(exchangeRateRepository, externalExchangeRateService,
                Clock.system(ZoneId.of("America/Sao_Paulo")));
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
            LedgerAccess ledgerAccess
    ) {
        return new EnvelopeServiceBean(envelopeRepository, allocationRepository, mappingRepository, ledgerAccess);
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
