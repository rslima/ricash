package com.rslima.ricash.ledgers.envelopes;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EnvelopeService {
    Page<Envelope> listLedgerEnvelopes(String userId, String ledgerSlug, PageRequest pageRequest);

    Optional<Envelope> find(String userId, String ledgerSlug, String envelopeId);

    Envelope create(String userId, String ledgerSlug, CreateEnvelopeRequest request);

    Envelope update(String userId, String ledgerSlug, String envelopeId, UpdateEnvelopeRequest request);

    void delete(String userId, String ledgerSlug, String envelopeId);

    EnvelopeAllocation allocate(String userId, String ledgerSlug, String envelopeId, AllocateEnvelopeRequest request);

    EnvelopeBalance getBalance(String userId, String ledgerSlug, String envelopeId, int year, int month);

    List<EnvelopeBalance> getBudgetSummary(String userId, String ledgerSlug, int year, int month);

    List<String> getEnvelopeAccounts(String userId, String ledgerSlug, String envelopeId);

    void setEnvelopeAccounts(String userId, String ledgerSlug, String envelopeId, List<String> accountIds);

    Map<String, String> getAllEnvelopeMappings(String userId, String ledgerSlug);

    BigDecimal getToBeBudgeted(String userId, String ledgerSlug, int year, int month);
}
