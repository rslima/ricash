package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerAccess;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentPriceServiceBeanTest {

    @Mock
    private InstrumentPriceRepository instrumentPriceRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    private InstrumentPriceServiceBean priceService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";
    private static final String INSTRUMENT_ID = "instrument-id";
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        priceService = new InstrumentPriceServiceBean(
                instrumentPriceRepository, instrumentRepository, new LedgerAccess(ledgerRepository));
    }

    private void givenLedgerExists() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
    }

    private void givenInstrumentInLedger() {
        givenLedgerExists();
        var instrument = new Instrument(INSTRUMENT_ID, LEDGER_ID, "PETR4", "Petrobras", InstrumentType.STOCK,
                "BRL", "B3", null, InstrumentStatus.ACTIVE, Instant.now());
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.of(instrument));
    }

    @Test
    void savePrice_validPrice() {
        givenInstrumentInLedger();
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = priceService.savePrice(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL");

        assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("25.500000"));
        assertThat(result.source()).isEqualTo("MANUAL");

        var captor = ArgumentCaptor.forClass(InstrumentPrice.class);
        verify(instrumentPriceRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isNotNull().hasSize(36);
    }

    @Test
    void savePrice_zeroPriceThrows() {
        givenInstrumentInLedger();
        assertThatThrownBy(() -> priceService.savePrice(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, BigDecimal.ZERO, DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void savePrice_negativePriceThrows() {
        givenInstrumentInLedger();
        assertThatThrownBy(() -> priceService.savePrice(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, new BigDecimal("-10"), DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void savePrice_foreignInstrument_throwsNotFound() {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceService.savePrice(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL"))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    void listByInstrument_checksOwnershipThenDelegates() {
        givenInstrumentInLedger();
        var pageable = PageRequest.of(0, 20);
        var price = new InstrumentPrice("id", INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL", Instant.now());
        when(instrumentPriceRepository.findByInstrumentId(INSTRUMENT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(price)));

        var result = priceService.listByInstrument(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listByInstrument_foreignInstrument_throwsNotFound() {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceService.listByInstrument(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, PageRequest.of(0, 20)))
                .isInstanceOf(InstrumentNotFoundException.class);
    }

    @Test
    void listByLedger_delegatesToRepository() {
        givenLedgerExists();
        var pageable = PageRequest.of(0, 20);
        var price = new InstrumentPrice("id", INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL", Instant.now());
        when(instrumentPriceRepository.findByLedgerId(LEDGER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(price)));

        var result = priceService.listByLedger(USER_ID, LEDGER_SLUG, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void delete_scopedDelete_throwsWhenNothingDeleted() {
        givenLedgerExists();
        when(instrumentPriceRepository.deleteById(LEDGER_ID, "price-id")).thenReturn(0);

        assertThatThrownBy(() -> priceService.delete(USER_ID, LEDGER_SLUG, "price-id"))
                .isInstanceOf(InstrumentPriceNotFoundException.class);
    }

    @Test
    void delete_scopedDelete_succeedsWhenRowDeleted() {
        givenLedgerExists();
        when(instrumentPriceRepository.deleteById(LEDGER_ID, "price-id")).thenReturn(1);

        priceService.delete(USER_ID, LEDGER_SLUG, "price-id");

        verify(instrumentPriceRepository).deleteById(LEDGER_ID, "price-id");
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", null, "BRL", Instant.now(), List.of());
    }
}
