package com.rslima.ricash.ledgers.instruments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class InstrumentServiceBean implements InstrumentService {

    private final InstrumentRepository instrumentRepository;

    @Override
    public Optional<Instrument> findById(String id) {
        return instrumentRepository.findById(id);
    }

    @Override
    public Optional<Instrument> findByLedgerAndSymbol(String ledgerId, String symbol) {
        return instrumentRepository.findByLedgerIdAndSymbol(ledgerId, symbol);
    }

    @Override
    public Page<Instrument> listByLedger(String ledgerId, Pageable pageable) {
        return instrumentRepository.findByLedgerId(ledgerId, pageable);
    }

    @Override
    public List<Instrument> listAllByLedger(String ledgerId) {
        return instrumentRepository.findAllByLedgerId(ledgerId);
    }

    @Override
    public Instrument create(String ledgerId, String symbol, String name, InstrumentType type,
                             String currency, String market, String isin) {
        log.info("Creating instrument {} for ledger {}", symbol, ledgerId);

        // Check for duplicate symbol in same ledger
        Optional<Instrument> existing = instrumentRepository.findByLedgerIdAndSymbol(ledgerId, symbol);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Instrument with symbol " + symbol + " already exists in this ledger");
        }

        Instrument instrument = new Instrument(
            UUID.randomUUID().toString(),
            ledgerId,
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
    public Instrument update(String id, String symbol, String name, InstrumentType type,
                             String currency, String market, String isin, InstrumentStatus status) {
        log.info("Updating instrument {}", id);

        Optional<Instrument> existing = instrumentRepository.findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Instrument not found: " + id);
        }

        Instrument current = existing.get();

        // Check if symbol is being changed and would conflict
        if (!current.symbol().equalsIgnoreCase(symbol)) {
            Optional<Instrument> conflict = instrumentRepository.findByLedgerIdAndSymbol(current.ledgerId(), symbol);
            if (conflict.isPresent()) {
                throw new IllegalArgumentException("Instrument with symbol " + symbol + " already exists in this ledger");
            }
        }

        Instrument updated = new Instrument(
            id,
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
    public void delete(String id) {
        log.info("Deleting instrument {}", id);
        instrumentRepository.deleteById(id);
    }
}
