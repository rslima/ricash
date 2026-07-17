package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.ledgers.MonetaryAmount;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class ExchangeRateServiceBean implements ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExternalExchangeRateService externalExchangeRateService;
    private final Clock clock;

    private static final int RATE_SCALE = 6;
    private static final int AMOUNT_SCALE = 2;

    @Override
    public Optional<BigDecimal> getRate(String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency.equals(toCurrency)) {
            return Optional.of(BigDecimal.ONE);
        }

        // Try to find direct rate (fromCurrency -> toCurrency) in database
        Optional<ExchangeRate> directRate = exchangeRateRepository.findRate(fromCurrency, toCurrency, date);
        if (directRate.isPresent()) {
            log.debug("Found direct rate in database from {} to {}: {}", fromCurrency, toCurrency, directRate.get().rate());
            return Optional.of(directRate.get().rate());
        }

        // Try to find inverse rate (toCurrency -> fromCurrency) in database and calculate inverse
        Optional<ExchangeRate> inverseRate = exchangeRateRepository.findRate(toCurrency, fromCurrency, date);
        if (inverseRate.isPresent()) {
            BigDecimal calculatedRate = BigDecimal.ONE.divide(inverseRate.get().rate(), RATE_SCALE, RoundingMode.HALF_UP);
            log.debug("Found inverse rate in database from {} to {} ({}), calculated as: {}",
                toCurrency, fromCurrency, inverseRate.get().rate(), calculatedRate);
            return Optional.of(calculatedRate);
        }

        // No rate in database - try external APIs
        log.info("No rate in database for {} to {} on {}, fetching from external API", fromCurrency, toCurrency, date);
        Optional<BigDecimal> externalRate = externalExchangeRateService.fetchRate(fromCurrency, toCurrency, date);

        if (externalRate.isPresent()) {
            // Save the fetched rate to database for future use
            try {
                saveRate(fromCurrency, toCurrency, externalRate.get(), date, "EXTERNAL_API");
                log.info("Saved external rate to database: {} -> {} = {}",
                    fromCurrency, toCurrency, externalRate.get());
            } catch (Exception e) {
                log.warn("Failed to save external rate to database: {}", e.getMessage());
                // Continue anyway - we have the rate even if we couldn't save it
            }
            return externalRate;
        }

        log.warn("No exchange rate found for {} to {} on date {} (checked database and external APIs)",
            fromCurrency, toCurrency, date);
        return Optional.empty();
    }

    @Override
    public Optional<MonetaryAmount> convert(MonetaryAmount amount, String toCurrency, LocalDate date) {
        if (amount.currency().equals(toCurrency)) {
            return Optional.of(amount);
        }

        Optional<BigDecimal> rate = getRate(amount.currency(), toCurrency, date);
        if (rate.isEmpty()) {
            log.warn("Cannot convert {} {} to {} - no rate available",
                amount.amount(), amount.currency(), toCurrency);
            return Optional.empty();
        }

        BigDecimal convertedAmount = amount.amount().multiply(rate.get())
            .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        log.debug("Converted {} {} to {} {} using rate {}",
            amount.amount(), amount.currency(), convertedAmount, toCurrency, rate.get());

        return Optional.of(new MonetaryAmount(convertedAmount, toCurrency));
    }

    @Override
    @Transactional
    public ExchangeRate saveRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate, String source) {
        if (fromCurrency.equals(toCurrency)) {
            throw new IllegalArgumentException("Cannot create exchange rate for same currency: " + fromCurrency);
        }

        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive: " + rate);
        }

        ExchangeRate exchangeRate = new ExchangeRate(
            UuidCreator.getTimeOrderedEpoch().toString(),
            fromCurrency.toUpperCase(),
            toCurrency.toUpperCase(),
            rate.setScale(RATE_SCALE, RoundingMode.HALF_UP),
            effectiveDate,
            source,
            Instant.now()
        );

        return exchangeRateRepository.save(exchangeRate);
    }

    @Override
    @Transactional
    public Optional<ExchangeRate> refreshRate(String fromCurrency, String toCurrency, LocalDate date) {
        String from = fromCurrency.toUpperCase();
        String to = toCurrency.toUpperCase();

        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot fetch exchange rate for same currency: " + from);
        }

        // Force-fetch from the external provider, bypassing the database cache.
        log.info("Force-refreshing external rate {} -> {} on {}", from, to, date);
        Optional<BigDecimal> externalRate = externalExchangeRateService.fetchRate(from, to, date);

        if (externalRate.isEmpty()) {
            log.warn("External provider returned no rate for {} -> {} on {}", from, to, date);
            return Optional.empty();
        }

        // saveRate upserts on (from, to, effectiveDate), so an existing rate for
        // that day is overwritten with the freshly fetched value.
        ExchangeRate saved = saveRate(from, to, externalRate.get(), date, "EXTERNAL_API");
        log.info("Saved refreshed external rate: {} {} -> {} = {}", from, to, saved.rate(), date);
        return Optional.of(saved);
    }

    // Deliberately NOT @Transactional: the sweep performs one external HTTP
    // call per pair and must not hold a pooled DB connection while doing so.
    // Each upsert is a single atomic statement and the operation is
    // idempotent, so an interrupted sweep simply completes on the next run.
    @Override
    public int refreshAllKnownRates() {
        final LocalDate today = LocalDate.now(clock);
        List<CurrencyPair> pairs = exchangeRateRepository.findDistinctExternalPairs();
        log.info("Refreshing exchange rates for {} known currency pair(s)", pairs.size());

        int refreshed = 0;
        for (CurrencyPair pair : pairs) {
            try {
                if (hasManualRateForToday(pair, today)) {
                    log.info("Skipping {} -> {}: today's rate was entered manually",
                        pair.fromCurrency(), pair.toCurrency());
                    continue;
                }
                // refreshRate already warns when the provider has no rate.
                if (refreshRate(pair.fromCurrency(), pair.toCurrency(), today).isPresent()) {
                    refreshed++;
                }
            } catch (RuntimeException e) {
                log.warn("Failed to refresh rate {} -> {}: {}",
                    pair.fromCurrency(), pair.toCurrency(), e.getMessage());
            }
        }
        return refreshed;
    }

    /** A rate the user entered for today must win over the scheduled fetch. */
    private boolean hasManualRateForToday(CurrencyPair pair, LocalDate today) {
        return exchangeRateRepository.findRate(pair.fromCurrency(), pair.toCurrency(), today)
            .filter(rate -> today.equals(rate.effectiveDate()))
            .filter(rate -> "MANUAL".equals(rate.source()))
            .isPresent();
    }

    @Override
    @Transactional
    public ExchangeRate saveManualRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate) {
        log.info("Saving manual exchange rate: {} -> {} = {} on {}", fromCurrency, toCurrency, rate, effectiveDate);
        return saveRate(fromCurrency, toCurrency, rate, effectiveDate, "MANUAL");
    }

    @Override
    public Page<ExchangeRate> listRates(Pageable pageable) {
        return exchangeRateRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public void deleteRate(String id) {
        log.info("Deleting exchange rate: {}", id);
        exchangeRateRepository.deleteById(id);
    }
}
