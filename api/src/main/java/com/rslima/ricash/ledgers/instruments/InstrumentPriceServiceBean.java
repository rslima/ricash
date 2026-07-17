package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;
import com.rslima.ricash.ledgers.LedgerAccess;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceServiceBean implements InstrumentPriceService {

    private final InstrumentPriceRepository instrumentPriceRepository;
    private final InstrumentRepository instrumentRepository;
    private final LedgerAccess ledgerAccess;
    private final EodhdPriceService eodhdPriceService;
    private final InstrumentPriceProviderProperties properties;

    private static final int PRICE_SCALE = 6;
    private static final String SOURCE_EODHD = "EODHD";

    /** Sanity floor for backfill requests; no provider serves prices before this anyway. */
    private static final LocalDate EPOCH_FLOOR = LocalDate.of(1970, 1, 1);

    @Override
    public Page<InstrumentPrice> listByInstrument(String userId, String ledgerSlug, String instrumentId, Pageable pageable) {
        requireInstrumentInLedger(userId, ledgerSlug, instrumentId);
        return instrumentPriceRepository.findByInstrumentId(instrumentId, pageable);
    }

    @Override
    public Page<InstrumentPrice> listByLedger(String userId, String ledgerSlug, Pageable pageable) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return instrumentPriceRepository.findByLedgerId(ledger.id(), pageable);
    }

    @Override
    public List<InstrumentPrice> getLatestPricesByLedger(String ledgerId) {
        return instrumentPriceRepository.findLatestPricesByLedgerId(ledgerId);
    }

    @Override
    @Transactional
    public InstrumentPrice savePrice(String userId, String ledgerSlug, String instrumentId,
                                     BigDecimal price, LocalDate effectiveDate, String source) {
        requireInstrumentInLedger(userId, ledgerSlug, instrumentId);
        log.info("Saving price {} for instrument {} on {}", price, instrumentId, effectiveDate);

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive: " + price);
        }

        InstrumentPrice instrumentPrice = new InstrumentPrice(
            UuidCreator.getTimeOrderedEpoch().toString(),
            instrumentId,
            price.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            effectiveDate,
            source,
            Instant.now()
        );

        return instrumentPriceRepository.save(instrumentPrice);
    }

    // Deliberately NOT @Transactional: the provider performs several sequential
    // HTTP calls and must not hold a pooled DB connection while doing so. Each
    // repository save is a single atomic upsert and the operation is idempotent,
    // so an interrupted backfill simply completes on the next fetch.
    @Override
    public InstrumentPrice fetchPrices(String userId, String ledgerSlug, String instrumentId, LocalDate from) {
        final var instrument = requireInstrumentInLedger(userId, ledgerSlug, instrumentId);

        if (instrument.isin() == null || instrument.isin().isBlank()) {
            throw new IllegalArgumentException(
                "Instrument %s has no ISIN; set one before fetching prices".formatted(instrument.symbol()));
        }
        if (from != null && from.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot backfill prices from a future date: " + from);
        }
        if (from != null && from.isBefore(EPOCH_FLOOR)) {
            throw new IllegalArgumentException("Cannot backfill prices from before %s: %s".formatted(EPOCH_FLOOR, from));
        }

        List<InstrumentPrice> saved = refreshFromProvider(instrument, from);
        if (saved.isEmpty()) {
            throw new InstrumentPriceNotAvailableException(instrument.symbol(), instrument.isin());
        }
        return saved.getLast();
    }

    @Override
    public int refreshAllActivePrices() {
        List<Instrument> instruments = instrumentRepository.findAllActiveWithIsinSystemWide();
        log.info("Refreshing prices for {} active instrument(s) with an ISIN", instruments.size());

        int refreshed = 0;
        for (int i = 0; i < instruments.size(); i++) {
            if (i > 0 && !throttle()) {
                break;
            }
            final var instrument = instruments.get(i);
            try {
                if (!refreshFromProvider(instrument, null).isEmpty()) {
                    refreshed++;
                } else {
                    log.warn("No price available for instrument {} (ISIN {})",
                        instrument.symbol(), instrument.isin());
                }
            } catch (RuntimeException e) {
                log.warn("Failed to refresh prices for instrument {} (ISIN {}): {}",
                    instrument.symbol(), instrument.isin(), e.getMessage());
            }
        }
        return refreshed;
    }

    private List<InstrumentPrice> refreshFromProvider(Instrument instrument, LocalDate from) {
        List<InstrumentPrice> saved = eodhdPriceService
            .fetchQuotes(instrument.isin(), instrument.currency(), from)
            .stream()
            .filter(quote -> quote.price().compareTo(BigDecimal.ZERO) > 0)
            .map(quote -> instrumentPriceRepository.save(new InstrumentPrice(
                UuidCreator.getTimeOrderedEpoch().toString(),
                instrument.id(),
                quote.price().setScale(PRICE_SCALE, RoundingMode.HALF_UP),
                quote.date(),
                SOURCE_EODHD,
                Instant.now()
            )))
            .toList();

        log.info("Upserted {} price(s) for instrument {} from {}",
            saved.size(), instrument.symbol(), SOURCE_EODHD);
        return saved;
    }

    /** Pauses between provider calls in the sweep; false when interrupted (abort). */
    private boolean throttle() {
        long millis = properties.refreshThrottleMillis();
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Instrument price refresh interrupted; aborting sweep");
            return false;
        }
    }

    @Override
    @Transactional
    public void delete(String userId, String ledgerSlug, String priceId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        log.info("Deleting price {}", priceId);

        int deleted = instrumentPriceRepository.deleteById(ledger.id(), priceId);
        if (deleted == 0) {
            throw new InstrumentPriceNotFoundException(priceId);
        }
    }

    private Instrument requireInstrumentInLedger(String userId, String ledgerSlug, String instrumentId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return instrumentRepository.findById(ledger.id(), instrumentId)
                .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));
    }
}
