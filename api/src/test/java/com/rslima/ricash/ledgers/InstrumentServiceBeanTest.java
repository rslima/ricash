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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceBeanTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    private InstrumentServiceBean instrumentService;

    private static final String LEDGER_ID = "ledger-id";
    private static final String INSTRUMENT_ID = "instrument-id";

    @BeforeEach
    void setUp() {
        instrumentService = new InstrumentServiceBean(instrumentRepository);
    }

    @Test
    void create_validInstrumentWithUppercaseSymbol() {
        when(instrumentRepository.findByLedgerIdAndSymbol(LEDGER_ID, "petr4")).thenReturn(Optional.empty());
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = instrumentService.create(LEDGER_ID, "petr4", "Petrobras PN", InstrumentType.STOCK, "brl", "B3", null);

        assertThat(result.symbol()).isEqualTo("PETR4");
        assertThat(result.currency()).isEqualTo("BRL");
        assertThat(result.status()).isEqualTo(InstrumentStatus.ACTIVE);
        assertThat(result.id()).isNotNull().hasSize(36);

        var captor = ArgumentCaptor.forClass(Instrument.class);
        verify(instrumentRepository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Petrobras PN");
    }

    @Test
    void create_duplicateSymbol_throws() {
        var existing = createTestInstrument();
        when(instrumentRepository.findByLedgerIdAndSymbol(LEDGER_ID, "PETR4")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> instrumentService.create(LEDGER_ID, "PETR4", "Petrobras", InstrumentType.STOCK, "BRL", "B3", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void update_existingInstrument() {
        var existing = createTestInstrument();
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(Optional.of(existing));
        when(instrumentRepository.update(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = instrumentService.update(INSTRUMENT_ID, "PETR4", "Updated Name", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE);

        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.symbol()).isEqualTo("PETR4");
    }

    @Test
    void update_notFound_throws() {
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.update(INSTRUMENT_ID, "PETR4", "Name", InstrumentType.STOCK, "BRL", null, null, InstrumentStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void update_symbolConflict_throws() {
        var existing = createTestInstrument();
        var conflict = new Instrument("other-id", LEDGER_ID, "VALE3", "Vale", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE, Instant.now());
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(Optional.of(existing));
        when(instrumentRepository.findByLedgerIdAndSymbol(LEDGER_ID, "VALE3")).thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> instrumentService.update(INSTRUMENT_ID, "VALE3", "Changed", InstrumentType.STOCK, "BRL", null, null, InstrumentStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void findById_delegatesToRepository() {
        var instrument = createTestInstrument();
        when(instrumentRepository.findById(INSTRUMENT_ID)).thenReturn(Optional.of(instrument));

        var result = instrumentService.findById(INSTRUMENT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(INSTRUMENT_ID);
    }

    @Test
    void delete_delegatesToRepository() {
        instrumentService.delete(INSTRUMENT_ID);

        verify(instrumentRepository).deleteById(INSTRUMENT_ID);
    }

    private Instrument createTestInstrument() {
        return new Instrument(INSTRUMENT_ID, LEDGER_ID, "PETR4", "Petrobras PN", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE, Instant.now());
    }
}
