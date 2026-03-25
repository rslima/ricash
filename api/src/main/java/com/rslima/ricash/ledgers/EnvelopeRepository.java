package com.rslima.ricash.ledgers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

public interface EnvelopeRepository {
    Page<Envelope> listLedgerEnvelopes(String ledgerId, PageRequest pageRequest);

    Optional<Envelope> findById(String ledgerId, String envelopeId);

    Envelope create(String ledgerId, Envelope envelope);

    Envelope update(String ledgerId, String envelopeId, String name, String description, EnvelopeType type, String currency, EnvelopeStatus status, String parentEnvelopeId);

    List<String> findChildEnvelopeIds(String ledgerId, String envelopeId);

    boolean hasAllocations(String envelopeId);

    boolean hasTransactionEntries(String envelopeId);

    void delete(String ledgerId, String envelopeId);
}
