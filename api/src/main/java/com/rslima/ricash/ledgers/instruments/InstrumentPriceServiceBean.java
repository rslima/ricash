package com.rslima.ricash.ledgers.instruments;

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

    private static final int PRICE_SCALE = 6;

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

    private void requireInstrumentInLedger(String userId, String ledgerSlug, String instrumentId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        instrumentRepository.findById(ledger.id(), instrumentId)
                .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));
    }
}
