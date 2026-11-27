package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.LedgerAccess;
import com.rslima.ricash.ledgers.accounts.AccountRef;
import com.rslima.ricash.ledgers.accounts.AccountRepository;
import com.rslima.ricash.ledgers.envelopes.EnvelopeAllocationRepository.MonthlyActivity;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public class EnvelopeServiceBean implements EnvelopeService {
    private final EnvelopeRepository envelopeRepository;
    private final EnvelopeAllocationRepository allocationRepository;
    private final EnvelopeAccountMappingRepository mappingRepository;
    private final LedgerAccess ledgerAccess;
    private final AccountRepository accountRepository;
    private final Clock clock;

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
        validateParent(ledger.id(), null, request.parentEnvelopeId());

        final var envelope = new Envelope(
                UuidCreator.getTimeOrderedEpoch().toString(),
                request.name(),
                request.description(),
                request.currency(),
                request.type(),
                EnvelopeStatus.ACTIVE,
                Instant.now(clock),
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
        validateParent(ledger.id(), envelopeId, request.parentEnvelopeId());

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
        List<String> envelopeIdsToDelete = new ArrayList<>(collectSubtreeIds(ledger.id(), envelopeId));

        // Check if any of the envelopes have transaction entries
        for (String id : envelopeIdsToDelete) {
            if (envelopeRepository.hasTransactionEntries(id)) {
                throw new EnvelopeHasTransactionsException(id);
            }
        }

        // Delete in reverse order (children first)
        Collections.reverse(envelopeIdsToDelete);
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

        // The mapping rewrite deletes existing mappings by account id, so a
        // foreign account id must be rejected before anything is touched.
        Set<String> owned = accountRepository.findRefsByIds(ledger.id(), accountIds).stream()
                .map(AccountRef::id)
                .collect(Collectors.toSet());
        for (String accountId : accountIds) {
            if (!owned.contains(accountId)) {
                throw new IllegalArgumentException("Account not found: " + accountId);
            }
        }

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

    /**
     * A parent must be another envelope of the same ledger, and (on update)
     * not the envelope itself or one of its descendants: a parent cycle would
     * make the subtree walk below never finish.
     *
     * @param envelopeId the envelope being updated, or null on create
     */
    private void validateParent(String ledgerId, String envelopeId, String parentEnvelopeId) {
        if (parentEnvelopeId == null) {
            return;
        }
        if (envelopeRepository.findIdsIn(ledgerId, List.of(parentEnvelopeId)).isEmpty()) {
            throw new IllegalArgumentException("Parent envelope not found: " + parentEnvelopeId);
        }
        if (envelopeId != null && collectSubtreeIds(ledgerId, envelopeId).contains(parentEnvelopeId)) {
            throw new IllegalArgumentException(
                    "An envelope cannot be its own parent or be moved under one of its descendants");
        }
    }

    /**
     * The envelope and all of its descendants, parents before children. The
     * visited set makes the walk terminate even if stored data holds a cycle.
     */
    private SequencedSet<String> collectSubtreeIds(String ledgerId, String envelopeId) {
        SequencedSet<String> visited = new LinkedHashSet<>();
        collectSubtreeIds(ledgerId, envelopeId, visited);
        return visited;
    }

    private void collectSubtreeIds(String ledgerId, String envelopeId, SequencedSet<String> visited) {
        if (!visited.add(envelopeId)) {
            return;
        }
        for (String childId : envelopeRepository.findChildEnvelopeIds(ledgerId, envelopeId)) {
            collectSubtreeIds(ledgerId, childId, visited);
        }
    }

}
