package com.rslima.ricash.ledgers.instruments;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing financial instruments. Every operation takes the
 * caller's userId plus the ledger slug and resolves ownership internally, so
 * an instrument outside the caller's ledger behaves as if it did not exist.
 */
public interface InstrumentService {

    Optional<Instrument> find(String userId, String ledgerSlug, String instrumentId);

    Page<Instrument> listByLedger(String userId, String ledgerSlug, Pageable pageable);

    List<Instrument> listAllByLedger(String userId, String ledgerSlug);

    Instrument create(String userId, String ledgerSlug, String symbol, String name, InstrumentType type,
                      String currency, String market, String isin);

    Instrument update(String userId, String ledgerSlug, String instrumentId, String symbol, String name,
                      InstrumentType type, String currency, String market, String isin, InstrumentStatus status);

    void delete(String userId, String ledgerSlug, String instrumentId);
}
