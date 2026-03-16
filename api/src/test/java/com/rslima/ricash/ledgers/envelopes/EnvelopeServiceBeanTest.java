package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvelopeServiceBeanTest {

    @Mock
    private EnvelopeRepository envelopeRepository;

    @Mock
    private EnvelopeAllocationRepository allocationRepository;

    @Mock
    private EnvelopeAccountMappingRepository mappingRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private EnvelopeServiceBean envelopeService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";
    private static final String ENVELOPE_ID = "envelope-id";

    @BeforeEach
    void setUp() {
        envelopeService = new EnvelopeServiceBean(envelopeRepository, allocationRepository, mappingRepository, ledgerRepository);
    }

    @Test
    void listLedgerEnvelopes_delegatesToRepository() {
        var pageRequest = PageRequest.of(0, 20);
        var envelope = createTestEnvelope();
        var page = new PageImpl<>(List.of(envelope), pageRequest, 1);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.listLedgerEnvelopes(LEDGER_ID, pageRequest)).thenReturn(page);

        var result = envelopeService.listLedgerEnvelopes(USER_ID, LEDGER_SLUG, pageRequest);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void find_delegatesToRepository() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));

        var result = envelopeService.find(USER_ID, LEDGER_SLUG, ENVELOPE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(ENVELOPE_ID);
    }

    @Test
    void create_generatesUUIDAndActiveStatus() {
        var request = new CreateEnvelopeRequest("Groceries", "Food budget", "USD", EnvelopeType.EXPENSE, null);
        var captor = ArgumentCaptor.forClass(Envelope.class);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.create(eq(LEDGER_ID), any(Envelope.class))).thenAnswer(inv -> inv.getArgument(1));

        var result = envelopeService.create(USER_ID, LEDGER_SLUG, request);

        verify(envelopeRepository).create(eq(LEDGER_ID), captor.capture());
        var captured = captor.getValue();
        assertThat(captured.id()).isNotNull().hasSize(36);
        assertThat(captured.name()).isEqualTo("Groceries");
        assertThat(captured.status()).isEqualTo(EnvelopeStatus.ACTIVE);
    }

    @Test
    void update_existingEnvelope() {
        var request = new UpdateEnvelopeRequest("Updated", "Desc", EnvelopeType.EXPENSE, "USD", EnvelopeStatus.ACTIVE, null);
        var updated = createTestEnvelope();

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));
        when(envelopeRepository.update(LEDGER_ID, ENVELOPE_ID, "Updated", "Desc", EnvelopeType.EXPENSE, "USD", EnvelopeStatus.ACTIVE, null))
                .thenReturn(updated);

        var result = envelopeService.update(USER_ID, LEDGER_SLUG, ENVELOPE_ID, request);

        assertThat(result).isNotNull();
    }

    @Test
    void update_notFound_throws() {
        var request = new UpdateEnvelopeRequest("Updated", "Desc", EnvelopeType.EXPENSE, "USD", EnvelopeStatus.ACTIVE, null);

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> envelopeService.update(USER_ID, LEDGER_SLUG, ENVELOPE_ID, request))
                .isInstanceOf(EnvelopeNotFoundException.class);
    }

    @Test
    void delete_leafEnvelope_cleansAllocationsAndMappings() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));
        when(envelopeRepository.findChildEnvelopeIds(LEDGER_ID, ENVELOPE_ID)).thenReturn(List.of());
        when(envelopeRepository.hasTransactionEntries(ENVELOPE_ID)).thenReturn(false);

        envelopeService.delete(USER_ID, LEDGER_SLUG, ENVELOPE_ID);

        verify(allocationRepository).deleteByEnvelopeId(ENVELOPE_ID);
        verify(mappingRepository).deleteByEnvelopeId(ENVELOPE_ID);
        verify(envelopeRepository).delete(LEDGER_ID, ENVELOPE_ID);
    }

    @Test
    void delete_parentWithChildren_deletesRecursively() {
        var childId = "child-envelope";

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));
        when(envelopeRepository.findChildEnvelopeIds(LEDGER_ID, ENVELOPE_ID)).thenReturn(List.of(childId));
        when(envelopeRepository.findChildEnvelopeIds(LEDGER_ID, childId)).thenReturn(List.of());
        when(envelopeRepository.hasTransactionEntries(ENVELOPE_ID)).thenReturn(false);
        when(envelopeRepository.hasTransactionEntries(childId)).thenReturn(false);

        envelopeService.delete(USER_ID, LEDGER_SLUG, ENVELOPE_ID);

        verify(envelopeRepository).delete(LEDGER_ID, childId);
        verify(envelopeRepository).delete(LEDGER_ID, ENVELOPE_ID);
    }

    @Test
    void delete_envelopeWithTransactions_throws() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));
        when(envelopeRepository.findChildEnvelopeIds(LEDGER_ID, ENVELOPE_ID)).thenReturn(List.of());
        when(envelopeRepository.hasTransactionEntries(ENVELOPE_ID)).thenReturn(true);

        assertThatThrownBy(() -> envelopeService.delete(USER_ID, LEDGER_SLUG, ENVELOPE_ID))
                .isInstanceOf(EnvelopeHasTransactionsException.class);
    }

    @Test
    void allocate_upsertsAllocation() {
        var request = new AllocateEnvelopeRequest(2026, 3, new BigDecimal("500.00"), "March budget");
        var allocation = new EnvelopeAllocation("alloc-id", ENVELOPE_ID, 2026, 3, new BigDecimal("500.00"), "March budget", Instant.now(), Instant.now());

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));
        when(allocationRepository.upsert(ENVELOPE_ID, 2026, 3, new BigDecimal("500.00"), "March budget"))
                .thenReturn(allocation);

        var result = envelopeService.allocate(USER_ID, LEDGER_SLUG, ENVELOPE_ID, request);

        assertThat(result.allocatedAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void getBalance_calculatesRolloverPlusAllocatedMinusSpent() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));

        // Current month: allocated 500, spent 300
        when(allocationRepository.findByEnvelopeIdAndPeriod(ENVELOPE_ID, 2026, 3))
                .thenReturn(Optional.of(new EnvelopeAllocation("id", ENVELOPE_ID, 2026, 3, new BigDecimal("500"), null, Instant.now(), Instant.now())));
        when(allocationRepository.calculateSpentForEnvelope(ENVELOPE_ID, 2026, 3))
                .thenReturn(new BigDecimal("300"));

        // Previous month: no activity -> rollover = 0
        when(allocationRepository.findByEnvelopeIdAndPeriod(ENVELOPE_ID, 2026, 2))
                .thenReturn(Optional.empty());
        when(allocationRepository.calculateSpentForEnvelope(ENVELOPE_ID, 2026, 2))
                .thenReturn(BigDecimal.ZERO);

        var result = envelopeService.getBalance(USER_ID, LEDGER_SLUG, ENVELOPE_ID, 2026, 3);

        assertThat(result.rollover()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.allocated()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(result.spent()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(result.available()).isEqualByComparingTo(new BigDecimal("200"));
    }

    @Test
    void getBalance_withRolloverFromPreviousMonth() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));

        // Current month (March): allocated 500, spent 100
        when(allocationRepository.findByEnvelopeIdAndPeriod(ENVELOPE_ID, 2026, 3))
                .thenReturn(Optional.of(new EnvelopeAllocation("id", ENVELOPE_ID, 2026, 3, new BigDecimal("500"), null, Instant.now(), Instant.now())));
        when(allocationRepository.calculateSpentForEnvelope(ENVELOPE_ID, 2026, 3))
                .thenReturn(new BigDecimal("100"));

        // Previous month (February): allocated 400, spent 200 -> rollover available = 200
        when(allocationRepository.findByEnvelopeIdAndPeriod(ENVELOPE_ID, 2026, 2))
                .thenReturn(Optional.of(new EnvelopeAllocation("id2", ENVELOPE_ID, 2026, 2, new BigDecimal("400"), null, Instant.now(), Instant.now())));
        when(allocationRepository.calculateSpentForEnvelope(ENVELOPE_ID, 2026, 2))
                .thenReturn(new BigDecimal("200"));

        // January: no activity (stops recursion)
        when(allocationRepository.findByEnvelopeIdAndPeriod(ENVELOPE_ID, 2026, 1))
                .thenReturn(Optional.empty());
        when(allocationRepository.calculateSpentForEnvelope(ENVELOPE_ID, 2026, 1))
                .thenReturn(BigDecimal.ZERO);

        var result = envelopeService.getBalance(USER_ID, LEDGER_SLUG, ENVELOPE_ID, 2026, 3);

        assertThat(result.rollover()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(result.allocated()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(result.spent()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(result.available()).isEqualByComparingTo(new BigDecimal("600"));
    }

    @Test
    void getToBeBudgeted_calculatesIncomeMinusAllocated() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(allocationRepository.calculateIncomeForPeriod(LEDGER_ID, 2026, 3)).thenReturn(new BigDecimal("5000"));
        when(allocationRepository.sumAllocatedForPeriod(LEDGER_ID, 2026, 3)).thenReturn(new BigDecimal("3000"));

        var result = envelopeService.getToBeBudgeted(USER_ID, LEDGER_SLUG, 2026, 3);

        assertThat(result).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void getBudgetSummary_returnsBalanceForAllEnvelopes() {
        var envelope1 = new Envelope("env-1", "Groceries", null, "USD", EnvelopeType.EXPENSE, EnvelopeStatus.ACTIVE, Instant.now(), null, List.of());
        var envelope2 = new Envelope("env-2", "Rent", null, "USD", EnvelopeType.EXPENSE, EnvelopeStatus.ACTIVE, Instant.now(), null, List.of());

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.listLedgerEnvelopes(eq(LEDGER_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(envelope1, envelope2)));

        // env-1 balance
        when(allocationRepository.findByEnvelopeIdAndPeriod("env-1", 2026, 3)).thenReturn(Optional.empty());
        when(allocationRepository.calculateSpentForEnvelope("env-1", 2026, 3)).thenReturn(BigDecimal.ZERO);
        when(allocationRepository.findByEnvelopeIdAndPeriod("env-1", 2026, 2)).thenReturn(Optional.empty());
        when(allocationRepository.calculateSpentForEnvelope("env-1", 2026, 2)).thenReturn(BigDecimal.ZERO);

        // env-2 balance
        when(allocationRepository.findByEnvelopeIdAndPeriod("env-2", 2026, 3)).thenReturn(Optional.empty());
        when(allocationRepository.calculateSpentForEnvelope("env-2", 2026, 3)).thenReturn(BigDecimal.ZERO);
        when(allocationRepository.findByEnvelopeIdAndPeriod("env-2", 2026, 2)).thenReturn(Optional.empty());
        when(allocationRepository.calculateSpentForEnvelope("env-2", 2026, 2)).thenReturn(BigDecimal.ZERO);

        var result = envelopeService.getBudgetSummary(USER_ID, LEDGER_SLUG, 2026, 3);

        assertThat(result).hasSize(2);
    }

    @Test
    void getEnvelopeAccounts_returnsMappedAccountIds() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(envelopeRepository.findById(LEDGER_ID, ENVELOPE_ID)).thenReturn(Optional.of(createTestEnvelope()));
        when(mappingRepository.findByEnvelopeId(ENVELOPE_ID)).thenReturn(List.of(
                new EnvelopeAccountMapping("m1", ENVELOPE_ID, "acc-1"),
                new EnvelopeAccountMapping("m2", ENVELOPE_ID, "acc-2")
        ));

        var result = envelopeService.getEnvelopeAccounts(USER_ID, LEDGER_SLUG, ENVELOPE_ID);

        assertThat(result).containsExactly("acc-1", "acc-2");
    }

    @Test
    void getAllEnvelopeMappings_delegatesToRepository() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
        when(mappingRepository.findAllMappingsForLedger(LEDGER_ID)).thenReturn(Map.of("acc-1", "env-1"));

        var result = envelopeService.getAllEnvelopeMappings(USER_ID, LEDGER_SLUG);

        assertThat(result).containsEntry("acc-1", "env-1");
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", "Description", "USD", Instant.now(), List.of(), List.of());
    }

    private Envelope createTestEnvelope() {
        return new Envelope(ENVELOPE_ID, "Groceries", "Food budget", "USD", EnvelopeType.EXPENSE, EnvelopeStatus.ACTIVE, Instant.now(), null, List.of());
    }
}
