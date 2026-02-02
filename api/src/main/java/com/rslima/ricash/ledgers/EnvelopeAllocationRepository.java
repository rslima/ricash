package com.rslima.ricash.ledgers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EnvelopeAllocationRepository {
    List<EnvelopeAllocation> findByEnvelopeId(String envelopeId);

    Optional<EnvelopeAllocation> findByEnvelopeIdAndPeriod(String envelopeId, int year, int month);

    EnvelopeAllocation upsert(String envelopeId, int year, int month, BigDecimal allocatedAmount, String notes);

    void deleteByEnvelopeId(String envelopeId);

    BigDecimal sumAllocatedForPeriod(String ledgerId, int year, int month);

    BigDecimal calculateSpentForEnvelope(String envelopeId, int year, int month);

    BigDecimal calculateIncomeForPeriod(String ledgerId, int year, int month);
}
