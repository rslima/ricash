package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateServiceBean implements ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExternalExchangeRateService externalExchangeRateService;

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
                log.info("Saved external rate to database: {} {} -> {} = {}",
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
    public ExchangeRate saveRate(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate effectiveDate, String source) {
        if (fromCurrency.equals(toCurrency)) {
            throw new IllegalArgumentException("Cannot create exchange rate for same currency: " + fromCurrency);
        }

        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive: " + rate);
        }

        ExchangeRate exchangeRate = new ExchangeRate(
            UUID.randomUUID().toString(),
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
    public Optional<BigDecimal> getLatestRate(String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return Optional.of(BigDecimal.ONE);
        }

        // Try direct rate first
        Optional<ExchangeRate> directRate = exchangeRateRepository.findLatestRate(fromCurrency, toCurrency);
        if (directRate.isPresent()) {
            return Optional.of(directRate.get().rate());
        }

        // Try inverse rate
        Optional<ExchangeRate> inverseRate = exchangeRateRepository.findLatestRate(toCurrency, fromCurrency);
        if (inverseRate.isPresent()) {
            return Optional.of(BigDecimal.ONE.divide(inverseRate.get().rate(), RATE_SCALE, RoundingMode.HALF_UP));
        }

        return Optional.empty();
    }
}
