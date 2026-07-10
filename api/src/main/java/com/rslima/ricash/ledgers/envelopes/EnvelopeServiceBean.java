package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.LedgerAccess;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationRepository.MonthlyActivity;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class EnvelopeServiceBean implements EnvelopeService {
    private final EnvelopeRepository envelopeRepository;
    private final EnvelopeAllocationRepository allocationRepository;
    private final EnvelopeAccountMappingRepository mappingRepository;
    private final LedgerAccess ledgerAccess;

    @Override
    public Page<Envelope> listLedgerEnvelopes(String userId, String ledgerSlug, Pageable pageRequest) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return envelopeRepository.listLedgerEnvelopes(ledger.id(), pageRequest);
    }

    @Override
    public Optional<Envelope> find(String userId, String ledgerSlug, String envelopeId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return envelopeRepository.findById(ledger.id(), envelopeId);
    }

    @Override
    @Transactional
    public Envelope create(String userId, String ledgerSlug, CreateEnvelopeRequest request) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        final var envelope = new Envelope(
                UuidCreator.getTimeOrderedEpoch().toString(),
                request.name(),
                request.description(),
                request.currency(),
                request.type(),
                EnvelopeStatus.ACTIVE,
                Instant.now(),
                request.parentEnvelopeId(),
                List.of()
        );

        return envelopeRepository.create(ledger.id(), envelope);
    }

    @Override
    @Transactional
    public Envelope update(String userId, String ledgerSlug, String envelopeId, UpdateEnvelopeRequest request) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        return envelopeRepository.update(
                ledger.id(),
                envelopeId,
                request.name(),
                request.description(),
                request.type(),
                request.currency(),
                request.status(),
                request.parentEnvelopeId()
        );
    }

    @Override
    @Transactional
    public void delete(String userId, String ledgerSlug, String envelopeId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        // Collect all envelope IDs to delete (envelope + all descendants)
        List<String> envelopeIdsToDelete = new ArrayList<>();
        collectEnvelopeIdsRecursively(ledger.id(), envelopeId, envelopeIdsToDelete);

        // Check if any of the envelopes have transaction entries
        for (String id : envelopeIdsToDelete) {
            if (envelopeRepository.hasTransactionEntries(id)) {
                throw new EnvelopeHasTransactionsException(id);
            }
        }

        // Delete in reverse order (children first)
        java.util.Collections.reverse(envelopeIdsToDelete);
        for (String id : envelopeIdsToDelete) {
            // Delete allocations first
            allocationRepository.deleteByEnvelopeId(id);
            // Delete account mappings
            mappingRepository.deleteByEnvelopeId(id);
            // Delete the envelope
            envelopeRepository.delete(ledger.id(), id);
        }
    }

    @Override
    @Transactional
    public EnvelopeAllocation allocate(String userId, String ledgerSlug, String envelopeId, AllocateEnvelopeRequest request) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        return allocationRepository.upsert(
                envelopeId,
                request.year(),
                request.month(),
                request.allocatedAmount(),
                request.notes()
        );
    }

    @Override
    public EnvelopeBalance getBalance(String userId, String ledgerSlug, String envelopeId, int year, int month) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        var activity = allocationRepository.findMonthlyActivityByEnvelope(envelopeId, year, month);
        return balanceFromActivity(envelopeId, activityByMonthIndex(activity), year, month);
    }

    @Override
    public List<EnvelopeBalance> getBudgetSummary(String userId, String ledgerSlug, int year, int month) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        // One query for the envelopes and one for all their monthly activity,
        // replacing the former two-queries-per-envelope-month recursion.
        var envelopes = envelopeRepository.findAllByLedger(ledger.id());
        Map<String, List<MonthlyActivity>> activityByEnvelope = allocationRepository
                .findMonthlyActivityByLedger(ledger.id(), year, month).stream()
                .collect(Collectors.groupingBy(MonthlyActivity::envelopeId));

        return envelopes.stream()
                .map(envelope -> balanceFromActivity(
                        envelope.id(),
                        activityByMonthIndex(activityByEnvelope.getOrDefault(envelope.id(), List.of())),
                        year, month))
                .toList();
    }

    @Override
    public List<String> getEnvelopeAccounts(String userId, String ledgerSlug, String envelopeId) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        return mappingRepository.findByEnvelopeId(envelopeId).stream()
                .map(EnvelopeAccountMapping::accountId)
                .toList();
    }

    @Override
    @Transactional
    public void setEnvelopeAccounts(String userId, String ledgerSlug, String envelopeId, List<String> accountIds) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        mappingRepository.setMappingsForEnvelope(envelopeId, accountIds);
    }

    @Override
    public Map<String, String> getAllEnvelopeMappings(String userId, String ledgerSlug) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        return mappingRepository.findAllMappingsForLedger(ledger.id());
    }

    @Override
    public BigDecimal getToBeBudgeted(String userId, String ledgerSlug, int year, int month) {
        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);

        // Income for the month
        BigDecimal income = allocationRepository.calculateIncomeForPeriod(ledger.id(), year, month);

        // Total allocated for the month
        BigDecimal allocated = allocationRepository.sumAllocatedForPeriod(ledger.id(), year, month);

        // To Be Budgeted = Income - Allocated
        return income.subtract(allocated);
    }

    /**
     * Rollover semantics, unchanged from the original month-by-month
     * recursion (pinned by EnvelopeRolloverGoldenTest):
     * only months with an allocation or spending count as active; the carry
     * starts at the beginning of the run of consecutive active months
     * immediately preceding the target (nothing rolls across a gap month);
     * each month carries forward max(rollover + allocated - spent, 0);
     * months before 2020-01 are never considered (repository floor).
     */
    private EnvelopeBalance balanceFromActivity(String envelopeId, Map<Integer, MonthlyActivity> byMonth, int year, int month) {
        var current = byMonth.get(monthIndex(year, month));
        BigDecimal allocated = current != null ? current.allocated() : BigDecimal.ZERO;
        BigDecimal spent = current != null ? current.spent() : BigDecimal.ZERO;
        BigDecimal rollover = rolloverInto(byMonth, monthIndex(year, month));

        // Available = Rollover + Allocated - Spent
        BigDecimal available = rollover.add(allocated).subtract(spent);

        return new EnvelopeBalance(envelopeId, year, month, rollover, allocated, spent, available);
    }

    private BigDecimal rolloverInto(Map<Integer, MonthlyActivity> byMonth, int targetIndex) {
        int runStart = targetIndex;
        while (byMonth.containsKey(runStart - 1)) {
            runStart--;
        }

        BigDecimal carry = BigDecimal.ZERO;
        for (int monthIdx = runStart; monthIdx < targetIndex; monthIdx++) {
            var activity = byMonth.get(monthIdx);
            BigDecimal available = carry.add(activity.allocated()).subtract(activity.spent());
            carry = available.max(BigDecimal.ZERO);
        }
        return carry;
    }

    private Map<Integer, MonthlyActivity> activityByMonthIndex(List<MonthlyActivity> rows) {
        return rows.stream()
                // A month with neither allocation nor spending behaves as a gap.
                .filter(row -> row.allocated().signum() != 0 || row.spent().signum() != 0)
                .collect(Collectors.toMap(row -> monthIndex(row.periodYear(), row.periodMonth()), row -> row));
    }

    private static int monthIndex(int year, int month) {
        return year * 12 + (month - 1);
    }

    private void collectEnvelopeIdsRecursively(String ledgerId, String envelopeId, List<String> envelopeIds) {
        envelopeIds.add(envelopeId);
        List<String> childIds = envelopeRepository.findChildEnvelopeIds(ledgerId, envelopeId);
        for (String childId : childIds) {
            collectEnvelopeIdsRecursively(ledgerId, childId, envelopeIds);
        }
    }

}
