package com.rslima.ricash.ledgers;

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

    private InstrumentPriceServiceBean priceService;

    private static final String INSTRUMENT_ID = "instrument-id";
    private static final String LEDGER_ID = "ledger-id";
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        priceService = new InstrumentPriceServiceBean(instrumentPriceRepository);
    }

    @Test
    void savePrice_validPrice() {
        when(instrumentPriceRepository.save(any(InstrumentPrice.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = priceService.savePrice(INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL");

        assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("25.500000"));
        assertThat(result.source()).isEqualTo("MANUAL");

        var captor = ArgumentCaptor.forClass(InstrumentPrice.class);
        verify(instrumentPriceRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isNotNull().hasSize(36);
    }

    @Test
    void savePrice_zeroPriceThrows() {
        assertThatThrownBy(() -> priceService.savePrice(INSTRUMENT_ID, BigDecimal.ZERO, DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void savePrice_negativePriceThrows() {
        assertThatThrownBy(() -> priceService.savePrice(INSTRUMENT_ID, new BigDecimal("-10"), DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void getPrice_delegatesToRepository() {
        var price = new InstrumentPrice("id", INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL", Instant.now());
        when(instrumentPriceRepository.findPrice(INSTRUMENT_ID, DATE)).thenReturn(Optional.of(price));

        var result = priceService.getPrice(INSTRUMENT_ID, DATE);

        assertThat(result).contains(new BigDecimal("25.50"));
    }

    @Test
    void getLatestPrice_delegatesToRepository() {
        var price = new InstrumentPrice("id", INSTRUMENT_ID, new BigDecimal("30.00"), DATE, "API", Instant.now());
        when(instrumentPriceRepository.findLatestPrice(INSTRUMENT_ID)).thenReturn(Optional.of(price));

        var result = priceService.getLatestPrice(INSTRUMENT_ID);

        assertThat(result).contains(new BigDecimal("30.00"));
    }

    @Test
    void listByInstrument_delegatesToRepository() {
        var pageable = PageRequest.of(0, 20);
        var price = new InstrumentPrice("id", INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL", Instant.now());
        when(instrumentPriceRepository.findByInstrumentId(INSTRUMENT_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(price)));

        var result = priceService.listByInstrument(INSTRUMENT_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listByLedger_delegatesToRepository() {
        var pageable = PageRequest.of(0, 20);
        var price = new InstrumentPrice("id", INSTRUMENT_ID, new BigDecimal("25.50"), DATE, "MANUAL", Instant.now());
        when(instrumentPriceRepository.findByLedgerId(LEDGER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(price)));

        var result = priceService.listByLedger(LEDGER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
