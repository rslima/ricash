package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceServiceBean implements InstrumentPriceService {

    private final InstrumentPriceRepository instrumentPriceRepository;

    private static final int PRICE_SCALE = 6;

    @Override
    public Optional<BigDecimal> getPrice(String instrumentId, LocalDate date) {
        return instrumentPriceRepository.findPrice(instrumentId, date)
            .map(InstrumentPrice::price);
    }

    @Override
    public Optional<BigDecimal> getLatestPrice(String instrumentId) {
        return instrumentPriceRepository.findLatestPrice(instrumentId)
            .map(InstrumentPrice::price);
    }

    @Override
    public Page<InstrumentPrice> listByInstrument(String instrumentId, Pageable pageable) {
        return instrumentPriceRepository.findByInstrumentId(instrumentId, pageable);
    }

    @Override
    public Page<InstrumentPrice> listByLedger(String ledgerId, Pageable pageable) {
        return instrumentPriceRepository.findByLedgerId(ledgerId, pageable);
    }

    @Override
    public List<InstrumentPrice> getLatestPricesByLedger(String ledgerId) {
        return instrumentPriceRepository.findLatestPricesByLedgerId(ledgerId);
    }

    @Override
    public InstrumentPrice savePrice(String instrumentId, BigDecimal price, LocalDate effectiveDate, String source) {
        log.info("Saving price {} for instrument {} on {}", price, instrumentId, effectiveDate);

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive: " + price);
        }

        InstrumentPrice instrumentPrice = new InstrumentPrice(
            UUID.randomUUID().toString(),
            instrumentId,
            price.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
            effectiveDate,
            source,
            Instant.now()
        );

        return instrumentPriceRepository.save(instrumentPrice);
    }

    @Override
    public void delete(String id) {
        log.info("Deleting price {}", id);
        instrumentPriceRepository.deleteById(id);
    }
}
