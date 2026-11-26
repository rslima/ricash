package com.rslima.ricash.ledgers.envelopes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EnvelopeRepository {
    Page<Envelope> listLedgerEnvelopes(String ledgerId, Pageable pageRequest);

    /** All envelopes of the ledger, in the same (type, name) order as the paged listing. */
    List<Envelope> findAllByLedger(String ledgerId);

    Optional<Envelope> findById(String ledgerId, String envelopeId);

    /** The subset of {@code envelopeIds} that belong to the ledger. */
    Set<String> findIdsIn(String ledgerId, Collection<String> envelopeIds);

    Envelope create(String ledgerId, Envelope envelope);

    Envelope update(String ledgerId, String envelopeId, String name, String description, EnvelopeType type, String currency, EnvelopeStatus status, String parentEnvelopeId);

    List<String> findChildEnvelopeIds(String ledgerId, String envelopeId);

    boolean hasTransactionEntries(String envelopeId);

    void delete(String ledgerId, String envelopeId);
}
