package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentPriceServiceBeanTest {

    @Mock
    private InstrumentPriceRepository instrumentPriceRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private YahooFinancePriceService yahooFinancePriceService;

    private InstrumentPriceServiceBean priceService;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "ledger-id";
    private static final String LEDGER_SLUG = "test-ledger";
    private static final String INSTRUMENT_ID = "instrument-id";
    private static final String ISIN = "BRPETRACNPR6";
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        priceService = new InstrumentPriceServiceBean(
                instrumentPriceRepository, instrumentRepository, new LedgerAccess(ledgerRepository),
                yahooFinancePriceService, new InstrumentPriceProviderProperties(null, null, null, 0L));
    }

    private void givenLedgerExists() {
        when(ledgerRepository.findBySlug(USER_ID, LEDGER_SLUG)).thenReturn(Optional.of(createTestLedger()));
    }

    private void givenInstrumentInLedger() {
        givenInstrumentInLedger(null);
    }

    private void givenInstrumentInLedger(String isin) {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID))
                .thenReturn(Optional.of(instrumentWithIsin(INSTRUMENT_ID, isin)));
    }

    private Instrument instrumentWithIsin(String id, String isin) {
        return new Instrument(id, LEDGER_ID, "PETR4", "Petrobras", InstrumentType.STOCK,
                "BRL", "B3", isin, InstrumentStatus.ACTIVE, Instant.now());
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
    void fetchPrices_savesQuotesAndReturnsLatest() {
        givenInstrumentInLedger(ISIN);
        when(yahooFinancePriceService.fetchQuotes(ISIN, "BRL", null)).thenReturn(List.of(
                new YahooFinancePriceService.Quote(DATE.minusDays(1), new BigDecimal("24.80")),
                new YahooFinancePriceService.Quote(DATE, new BigDecimal("25.50"))));
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        var latest = priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, null);

        assertThat(latest.effectiveDate()).isEqualTo(DATE);
        assertThat(latest.price()).isEqualByComparingTo(new BigDecimal("25.500000"));
        assertThat(latest.source()).isEqualTo("YAHOO");

        var captor = ArgumentCaptor.forClass(InstrumentPrice.class);
        verify(instrumentPriceRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(InstrumentPrice::effectiveDate)
                .containsExactly(DATE.minusDays(1), DATE);
    }

    @Test
    void fetchPrices_passesBackfillDateToProvider() {
        givenInstrumentInLedger(ISIN);
        var from = DATE.minusMonths(1);
        when(yahooFinancePriceService.fetchQuotes(ISIN, "BRL", from)).thenReturn(List.of(
                new YahooFinancePriceService.Quote(DATE, new BigDecimal("25.50"))));
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, from);

        verify(yahooFinancePriceService).fetchQuotes(ISIN, "BRL", from);
    }

    @Test
    void fetchPrices_withoutIsin_throws() {
        givenInstrumentInLedger();

        assertThatThrownBy(() -> priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISIN");
        verifyNoInteractions(yahooFinancePriceService);
    }

    @Test
    void fetchPrices_futureFromDate_throws() {
        givenInstrumentInLedger(ISIN);

        assertThatThrownBy(() -> priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID,
                LocalDate.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
        verifyNoInteractions(yahooFinancePriceService);
    }

    @Test
    void fetchPrices_providerHasNothing_throwsNotAvailable() {
        givenInstrumentInLedger(ISIN);
        when(yahooFinancePriceService.fetchQuotes(ISIN, "BRL", null)).thenReturn(List.of());

        assertThatThrownBy(() -> priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, null))
                .isInstanceOf(InstrumentPriceNotAvailableException.class);
    }

    @Test
    void fetchPrices_foreignInstrument_throwsNotFound() {
        givenLedgerExists();
        when(instrumentRepository.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, null))
                .isInstanceOf(InstrumentNotFoundException.class);
        verifyNoInteractions(yahooFinancePriceService);
    }

    @Test
    void fetchPrices_filtersNonPositiveQuotes() {
        givenInstrumentInLedger(ISIN);
        when(yahooFinancePriceService.fetchQuotes(ISIN, "BRL", null)).thenReturn(List.of(
                new YahooFinancePriceService.Quote(DATE.minusDays(1), BigDecimal.ZERO),
                new YahooFinancePriceService.Quote(DATE, new BigDecimal("25.50"))));
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        var latest = priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID, null);

        assertThat(latest.effectiveDate()).isEqualTo(DATE);
        verify(instrumentPriceRepository, times(1)).save(any(InstrumentPrice.class));
    }

    @Test
    void refreshAllActivePrices_continuesAfterSingleInstrumentFailure() {
        var failing = instrumentWithIsin("instr-1", "ISIN000000A1");
        var working = instrumentWithIsin("instr-2", "ISIN000000B2");
        when(instrumentRepository.findAllActiveWithIsinSystemWide()).thenReturn(List.of(failing, working));
        when(yahooFinancePriceService.fetchQuotes("ISIN000000A1", "BRL", null))
                .thenThrow(new IllegalStateException("boom"));
        when(yahooFinancePriceService.fetchQuotes("ISIN000000B2", "BRL", null)).thenReturn(List.of(
                new YahooFinancePriceService.Quote(DATE, new BigDecimal("25.50"))));
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(priceService.refreshAllActivePrices()).isEqualTo(1);
    }

    @Test
    void fetchPrices_fromBeforeEpoch_throws() {
        givenInstrumentInLedger(ISIN);

        assertThatThrownBy(() -> priceService.fetchPrices(USER_ID, LEDGER_SLUG, INSTRUMENT_ID,
                LocalDate.of(1969, 12, 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1970");
        verifyNoInteractions(yahooFinancePriceService);
    }

    @Test
    void refreshAllActivePrices_interrupted_abortsSweepAndRestoresFlag() {
        var throttled = new InstrumentPriceServiceBean(
                instrumentPriceRepository, instrumentRepository, new LedgerAccess(ledgerRepository),
                yahooFinancePriceService, new InstrumentPriceProviderProperties(null, null, null, 5L));
        when(instrumentRepository.findAllActiveWithIsinSystemWide()).thenReturn(List.of(
                instrumentWithIsin("instr-1", "ISIN000000A1"),
                instrumentWithIsin("instr-2", "ISIN000000B2")));
        when(yahooFinancePriceService.fetchQuotes("ISIN000000A1", "BRL", null)).thenReturn(List.of(
                new YahooFinancePriceService.Quote(DATE, new BigDecimal("25.50"))));
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        Thread.currentThread().interrupt();
        try {
            // The throttle before instrument 2 hits the interrupt and aborts.
            assertThat(throttled.refreshAllActivePrices()).isEqualTo(1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        verify(yahooFinancePriceService, times(1)).fetchQuotes(any(), any(), any());
    }

    @Test
    void refreshAllActivePrices_noEligibleInstruments_returnsZero() {
        when(instrumentRepository.findAllActiveWithIsinSystemWide()).thenReturn(List.of());

        assertThat(priceService.refreshAllActivePrices()).isZero();
        verifyNoInteractions(yahooFinancePriceService);
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
