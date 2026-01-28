package com.rslima.ricash.ledgers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceBeanTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private SlugService slugService;

    private LedgerServiceBean ledgerService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "01234567-89ab-cdef-0123-456789abcdef";
    private static final String LEDGER_SLUG = "test-ledger";

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerServiceBean(ledgerRepository, slugService);
    }

    @Test
    void listUserLedgers_delegatesToRepository() {
        var pageRequest = PageRequest.of(0, 20);
        var ledger = createTestLedger();
        var page = new PageImpl<>(List.of(ledger), pageRequest, 1);

        when(ledgerRepository.listUserLedgers(USER_ID, pageRequest)).thenReturn(page);

        var result = ledgerService.listUserLedgers(USER_ID, pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(LEDGER_ID);
        verify(ledgerRepository).listUserLedgers(USER_ID, pageRequest);
    }

    @Test
    void findBySlug_delegatesToRepository() {
        var ledger = createTestLedger();

        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(ledger));

        var result = ledgerService.findBySlug(USER_ID, LEDGER_SLUG);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(LEDGER_ID);
        verify(ledgerRepository).findBySlug(USER_ID, LEDGER_SLUG);
    }

    @Test
    void findBySlug_whenNotFound_returnsEmpty() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.empty());

        var result = ledgerService.findBySlug(USER_ID, LEDGER_SLUG);

        assertThat(result).isEmpty();
    }

    @Test
    void create_generatesUUIDv7AndTimestampAndSlug() {
        var request = new CreateLedgerRequest("New Ledger", "Description", "EUR");
        var captor = ArgumentCaptor.forClass(Ledger.class);

        when(slugService.slugify("New Ledger")).thenReturn("new-ledger");
        when(ledgerRepository.existsBySlug(any(), any())).thenReturn(false);
        when(ledgerRepository.create(any(Ledger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var beforeCreate = Instant.now();
        var result = ledgerService.create(USER_ID, request);
        var afterCreate = Instant.now();

        verify(ledgerRepository).create(captor.capture());
        var capturedLedger = captor.getValue();

        assertThat(capturedLedger.id()).isNotNull();
        assertThat(capturedLedger.id()).hasSize(36); // UUID format
        assertThat(capturedLedger.userId()).isEqualTo(USER_ID);
        assertThat(capturedLedger.slug()).isEqualTo("new-ledger");
        assertThat(capturedLedger.name()).isEqualTo("New Ledger");
        assertThat(capturedLedger.description()).isEqualTo("Description");
        assertThat(capturedLedger.currency()).isEqualTo("EUR");
        assertThat(capturedLedger.createdAt()).isAfterOrEqualTo(beforeCreate);
        assertThat(capturedLedger.createdAt()).isBeforeOrEqualTo(afterCreate);
        assertThat(capturedLedger.accounts()).isEmpty();
        assertThat(capturedLedger.transactions()).isEmpty();

        assertThat(result).isEqualTo(capturedLedger);
    }

    @Test
    void create_withNullDescription_createsLedger() {
        var request = new CreateLedgerRequest("New Ledger", null, "EUR");

        when(slugService.slugify("New Ledger")).thenReturn("new-ledger");
        when(ledgerRepository.existsBySlug(any(), any())).thenReturn(false);
        when(ledgerRepository.create(any(Ledger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = ledgerService.create(USER_ID, request);

        assertThat(result.description()).isNull();
    }

    @Test
    void create_generatesUniqueIds() {
        var request = new CreateLedgerRequest("Ledger", "Description", "USD");

        when(slugService.slugify("Ledger")).thenReturn("ledger");
        when(ledgerRepository.existsBySlug(any(), any())).thenReturn(false);
        when(ledgerRepository.create(any(Ledger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result1 = ledgerService.create(USER_ID, request);
        var result2 = ledgerService.create(USER_ID, request);

        assertThat(result1.id()).isNotEqualTo(result2.id());
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", "Test Description", "USD", Instant.now(), List.of(), List.of());
    }
}
