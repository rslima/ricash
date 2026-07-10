package com.rslima.ricash.ledgers.envelopes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EnvelopeAllocationRepository {

    /**
     * One envelope-month of budget activity: the allocated amount and the
     * spent total (summed across currencies, mirroring the historical
     * behavior) for that period.
     */
    record MonthlyActivity(String envelopeId, int periodYear, int periodMonth,
                           BigDecimal allocated, BigDecimal spent) {
    }

    List<EnvelopeAllocation> findByEnvelopeId(String envelopeId);

    Optional<EnvelopeAllocation> findByEnvelopeIdAndPeriod(String envelopeId, int year, int month);

    EnvelopeAllocation upsert(String envelopeId, int year, int month, BigDecimal allocatedAmount, String notes);

    void deleteByEnvelopeId(String envelopeId);

    BigDecimal sumAllocatedForPeriod(String ledgerId, int year, int month);

    BigDecimal calculateIncomeForPeriod(String ledgerId, int year, int month);

    /**
     * All envelope-months with an allocation or spending for every envelope of
     * the ledger, from 2020-01 (the historical rollover floor) up to and
     * including the given period. One query - this is what makes budget
     * balances O(1) queries instead of two per envelope-month.
     */
    List<MonthlyActivity> findMonthlyActivityByLedger(String ledgerId, int uptoYear, int uptoMonth);

    /** Same as {@link #findMonthlyActivityByLedger} but for a single envelope. */
    List<MonthlyActivity> findMonthlyActivityByEnvelope(String envelopeId, int uptoYear, int uptoMonth);
}
