package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerAccess;
import com.rslima.ricash.ledgers.LedgerNotFoundException;
import com.rslima.ricash.ledgers.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceBeanTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private InstrumentServiceBean instrumentService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";
    private static final String INSTRUMENT_ID = "instrument-id";

    @BeforeEach
    void setUp() {
        instrumentService = new InstrumentServiceBean(instrumentRepository, new LedgerAccess(ledgerRepository));
    }

    private void givenLedgerExists() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
    }

    @Test
    void create_validInstrumentWithUppercaseSymbol() {
        givenLedgerExists();
        when(instrumentRepository.findByLedgerIdAndSymbol(LEDGER_ID, "petr4")).thenReturn(Optional.empty());
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = instrumentService.create(USER_ID, LEDGER_SLUG, "petr4", "Petrobras PN", InstrumentType.STOCK, "brl", "B3", null);

        assertThat(result.symbol()).isEqualTo("PETR4");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.status()).isEqualTo(InstrumentStatus.ACTIVE);
        assertThat(result.id()).isNotNull().hasSize(36);
        assertThat(result.ledgerId()).isEqualTo(LEDGER_ID);

        var captor = ArgumentCaptor.forClass(Instrument.class);
        verify(instrumentRepository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Petrobras PN");
    }

    @Test
    void create_duplicateSymbol_throws() {
        givenLedgerExists();
        var existing = createTestInstrument();
        when(instrumentRepository.findByLedgerIdAndSymbol(LEDGER_ID, "PETR4")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instrumentService.create(USER_ID, LEDGER_SLUG, "PETR4", "Petrobras", InstrumentType.STOCK, "BRL", "B3", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_unknownLedger_throwsNotFound() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.create(USER_ID, LEDGER_SLUG, "PETR4", "Petrobras", InstrumentType.STOCK, "BRL", "B3", null))
                .isInstanceOf(LedgerNotFoundException.class);
    }

    @Test
    void update_existingInstrument() {
        givenLedgerExists();
        var existing = createTestInstrument();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.of(existing));
        when(instrumentRepository.update(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = instrumentService.update(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, "PETR4", "Updated Name", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE);

        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.symbol()).isEqualTo("PETR4");
    }

    @Test
    void update_notFoundInLedger_throwsInstrumentNotFound() {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.update(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, "PETR4", "Name", InstrumentType.STOCK, "BRL", null, null, InstrumentStatus.ACTIVE))
                .isInstanceOf(InstrumentNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void update_symbolConflict_throws() {
        givenLedgerExists();
        var existing = createTestInstrument();
        var conflict = new Instrument("other-id", LEDGER_ID, "VALE3", "Vale", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE, Instant.now());
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.of(existing));
        when(instrumentRepository.findByLedgerIdAndSymbol(LEDGER_ID, "VALE3")).thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> instrumentService.update(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, "VALE3", "Changed", InstrumentType.STOCK, "BRL", null, null, InstrumentStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void find_scopesLookupToCallersLedger() {
        givenLedgerExists();
        var instrument = createTestInstrument();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.of(instrument));

        var result = instrumentService.find(USER_ID, LEDGER_SLUG, INSTRUMENT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(INSTRUMENT_ID);
    }

    @Test
    void delete_scopesDeleteToCallersLedger() {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.of(createTestInstrument()));

        instrumentService.delete(USER_ID, LEDGER_SLUG, INSTRUMENT_ID);

        verify(instrumentRepository).deleteById(LEDGER_ID, INSTRUMENT_ID);
    }

    @Test
    void delete_foreignInstrument_throwsInstrumentNotFound() {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.delete(USER_ID, LEDGER_SLUG, INSTRUMENT_ID))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", null, "BRL", Instant.now(), List.of());
    }

    private Instrument createTestInstrument() {
        return new Instrument(INSTRUMENT_ID, LEDGER_ID, "PETR4", "Petrobras PN", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE, Instant.now());
    }
}
