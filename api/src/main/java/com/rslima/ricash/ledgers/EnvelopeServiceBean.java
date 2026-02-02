package com.rslima.ricash.ledgers;

import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class EnvelopeServiceBean implements EnvelopeService {
    private final EnvelopeRepository envelopeRepository;
    private final EnvelopeAllocationRepository allocationRepository;
    private final EnvelopeAccountMappingRepository mappingRepository;
    private final LedgerRepository ledgerRepository;

    @Override
    public Page<Envelope> listLedgerEnvelopes(String userId, String ledgerSlug, PageRequest pageRequest) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return envelopeRepository.listLedgerEnvelopes(ledger.id(), pageRequest);
    }

    @Override
    public Optional<Envelope> find(String userId, String ledgerSlug, String envelopeId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return envelopeRepository.findById(ledger.id(), envelopeId);
    }

    @Override
    public Envelope create(String userId, String ledgerSlug, CreateEnvelopeRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

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
    public Envelope update(String userId, String ledgerSlug, String envelopeId, UpdateEnvelopeRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

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
    public void delete(String userId, String ledgerSlug, String envelopeId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

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
    public EnvelopeAllocation allocate(String userId, String ledgerSlug, String envelopeId, AllocateEnvelopeRequest request) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

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
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        return calculateBalance(envelopeId, year, month);
    }

    @Override
    public List<EnvelopeBalance> getBudgetSummary(String userId, String ledgerSlug, int year, int month) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        var envelopes = envelopeRepository.listLedgerEnvelopes(ledger.id(), PageRequest.of(0, 1000));

        return envelopes.getContent().stream()
                .map(envelope -> calculateBalance(envelope.id(), year, month))
                .toList();
    }

    @Override
    public List<String> getEnvelopeAccounts(String userId, String ledgerSlug, String envelopeId) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        return mappingRepository.findByEnvelopeId(envelopeId).stream()
                .map(EnvelopeAccountMapping::accountId)
                .toList();
    }

    @Override
    public void setEnvelopeAccounts(String userId, String ledgerSlug, String envelopeId, List<String> accountIds) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        envelopeRepository.findById(ledger.id(), envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        mappingRepository.setMappingsForEnvelope(envelopeId, accountIds);
    }

    @Override
    public Map<String, String> getAllEnvelopeMappings(String userId, String ledgerSlug) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);
        return mappingRepository.findAllMappingsForLedger(ledger.id());
    }

    @Override
    public BigDecimal getToBeBudgeted(String userId, String ledgerSlug, int year, int month) {
        final var ledger = getLedgerBySlug(userId, ledgerSlug);

        // Income for the month
        BigDecimal income = allocationRepository.calculateIncomeForPeriod(ledger.id(), year, month);

        // Total allocated for the month
        BigDecimal allocated = allocationRepository.sumAllocatedForPeriod(ledger.id(), year, month);

        // To Be Budgeted = Income - Allocated
        return income.subtract(allocated);
    }

    private EnvelopeBalance calculateBalance(String envelopeId, int year, int month) {
        // Get allocation for this period
        BigDecimal allocated = allocationRepository.findByEnvelopeIdAndPeriod(envelopeId, year, month)
                .map(EnvelopeAllocation::allocatedAmount)
                .orElse(BigDecimal.ZERO);

        // Get spent for this period
        BigDecimal spent = allocationRepository.calculateSpentForEnvelope(envelopeId, year, month);

        // Calculate rollover from previous month
        BigDecimal rollover = calculateRollover(envelopeId, year, month);

        // Available = Rollover + Allocated - Spent
        BigDecimal available = rollover.add(allocated).subtract(spent);

        return new EnvelopeBalance(envelopeId, year, month, rollover, allocated, spent, available);
    }

    private BigDecimal calculateRollover(String envelopeId, int year, int month) {
        // Get previous month
        int prevMonth = month - 1;
        int prevYear = year;
        if (prevMonth < 1) {
            prevMonth = 12;
            prevYear = year - 1;
        }

        // Base case: don't go back before 2020
        if (prevYear < 2020) {
            return BigDecimal.ZERO;
        }

        // Check if there's any allocation for the previous month
        // If no allocation exists and no spending, assume this is before the envelope was used
        var prevAllocation = allocationRepository.findByEnvelopeIdAndPeriod(envelopeId, prevYear, prevMonth);
        BigDecimal prevAllocated = prevAllocation.map(EnvelopeAllocation::allocatedAmount).orElse(BigDecimal.ZERO);
        BigDecimal prevSpent = allocationRepository.calculateSpentForEnvelope(envelopeId, prevYear, prevMonth);

        // If no activity in previous month, check one more month back, but limit depth
        if (prevAllocated.compareTo(BigDecimal.ZERO) == 0 && prevSpent.compareTo(BigDecimal.ZERO) == 0) {
            // No activity - stop recursion here
            return BigDecimal.ZERO;
        }

        // Calculate previous month's rollover recursively
        BigDecimal prevRollover = calculateRollover(envelopeId, prevYear, prevMonth);

        BigDecimal prevAvailable = prevRollover.add(prevAllocated).subtract(prevSpent);

        // Only carry forward positive balances
        return prevAvailable.max(BigDecimal.ZERO);
    }

    private void collectEnvelopeIdsRecursively(String ledgerId, String envelopeId, List<String> envelopeIds) {
        envelopeIds.add(envelopeId);
        List<String> childIds = envelopeRepository.findChildEnvelopeIds(ledgerId, envelopeId);
        for (String childId : childIds) {
            collectEnvelopeIdsRecursively(ledgerId, childId, envelopeIds);
        }
    }

    private Ledger getLedgerBySlug(String userId, String ledgerSlug) {
        return ledgerRepository.findBySlug(userId, ledgerSlug)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerSlug));
    }
}
