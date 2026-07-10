package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.LedgerAccess;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class InstrumentServiceBean implements InstrumentService {

    private final InstrumentRepository instrumentRepository;
    private final LedgerAccess ledgerAccess;

    @Override
    public Optional<Instrument> find(String userId, String ledgerSlug, String instrumentId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return instrumentRepository.findById(ledger.id(), instrumentId);
    }

    @Override
    public Page<Instrument> listByLedger(String userId, String ledgerSlug, Pageable pageable) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return instrumentRepository.findByLedgerId(ledger.id(), pageable);
    }

    @Override
    public List<Instrument> listAllByLedger(String userId, String ledgerSlug) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return instrumentRepository.findAllByLedgerId(ledger.id());
    }

    @Override
    @Transactional
    public Instrument create(String userId, String ledgerSlug, String symbol, String name, InstrumentType type,
                             String currency, String market, String isin) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        log.info("Creating instrument {} for ledger {}", symbol, ledger.id());

        // Check for duplicate symbol in same ledger
        Optional<Instrument> existing = instrumentRepository.findByLedgerIdAndSymbol(ledger.id(), symbol);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Instrument with symbol " + symbol + " already exists in this ledger");
        }

        Instrument instrument = new Instrument(
            UuidCreator.getTimeOrderedEpoch().toString(),
            ledger.id(),
            symbol.toUpperCase(),
            name,
            type,
            currency.toUpperCase(),
            market,
            isin,
            InstrumentStatus.ACTIVE,
            Instant.now()
        );

        return instrumentRepository.save(instrument);
    }

    @Override
    @Transactional
    public Instrument update(String userId, String ledgerSlug, String instrumentId, String symbol, String name,
                             InstrumentType type, String currency, String market, String isin, InstrumentStatus status) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        log.info("Updating instrument {}", instrumentId);

        Instrument current = instrumentRepository.findById(ledger.id(), instrumentId)
                .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));

        // Check if symbol is being changed and would conflict
        if (!current.symbol().equalsIgnoreCase(symbol)) {
            Optional<Instrument> conflict = instrumentRepository.findByLedgerIdAndSymbol(current.ledgerId(), symbol);
            if (conflict.isPresent()) {
                throw new IllegalArgumentException("Instrument with symbol " + symbol + " already exists in this ledger");
            }
        }

        Instrument updated = new Instrument(
            instrumentId,
            current.ledgerId(),
            symbol.toUpperCase(),
            name,
            type,
            currency.toUpperCase(),
            market,
            isin,
            status,
            current.createdAt()
        );

        return instrumentRepository.update(updated);
    }

    @Override
    @Transactional
    public void delete(String userId, String ledgerSlug, String instrumentId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        log.info("Deleting instrument {}", instrumentId);

        instrumentRepository.findById(ledger.id(), instrumentId)
                .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));

        instrumentRepository.deleteById(ledger.id(), instrumentId);
    }
}
