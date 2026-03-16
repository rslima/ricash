package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.ledgers.MonetaryAmount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceBeanTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ExternalExchangeRateService externalExchangeRateService;

    private ExchangeRateServiceBean exchangeRateService;

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateServiceBean(exchangeRateRepository, externalExchangeRateService);
    }

    // --- getRate tests ---

    @Test
    void getRate_sameCurrency_returnsOne() {
        var result = exchangeRateService.getRate("USD", "USD", DATE);

        assertThat(result).contains(BigDecimal.ONE);
        verifyNoInteractions(exchangeRateRepository);
    }

    @Test
    void getRate_directRate_returnsRate() {
        var rate = new ExchangeRate("id", "USD", "BRL", new BigDecimal("5.50"), DATE, "MANUAL", Instant.now());
        when(exchangeRateRepository.findRate("USD", "BRL", DATE)).thenReturn(Optional.of(rate));

        var result = exchangeRateService.getRate("USD", "BRL", DATE);

        assertThat(result).contains(new BigDecimal("5.50"));
    }

    @Test
    void getRate_inverseRate_returnsCalculatedInverse() {
        var rate = new ExchangeRate("id", "BRL", "USD", new BigDecimal("5.00"), DATE, "MANUAL", Instant.now());
        when(exchangeRateRepository.findRate("USD", "BRL", DATE)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findRate("BRL", "USD", DATE)).thenReturn(Optional.of(rate));

        var result = exchangeRateService.getRate("USD", "BRL", DATE);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(BigDecimal.ONE.divide(new BigDecimal("5.00"), 6, RoundingMode.HALF_UP));
    }

    @Test
    void getRate_externalFallback_fetchesAndSavesRate() {
        when(exchangeRateRepository.findRate("USD", "BRL", DATE)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findRate("BRL", "USD", DATE)).thenReturn(Optional.empty());
        when(externalExchangeRateService.fetchRate("USD", "BRL", DATE)).thenReturn(Optional.of(new BigDecimal("5.50")));
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = exchangeRateService.getRate("USD", "BRL", DATE);

        assertThat(result).contains(new BigDecimal("5.50"));
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }

    @Test
    void getRate_externalFails_returnsEmpty() {
        when(exchangeRateRepository.findRate("USD", "BRL", DATE)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findRate("BRL", "USD", DATE)).thenReturn(Optional.empty());
        when(externalExchangeRateService.fetchRate("USD", "BRL", DATE)).thenReturn(Optional.empty());

        var result = exchangeRateService.getRate("USD", "BRL", DATE);

        assertThat(result).isEmpty();
    }

    // --- convert tests ---

    @Test
    void convert_sameCurrency_returnsSameAmount() {
        var amount = new MonetaryAmount(BigDecimal.TEN, "USD");

        var result = exchangeRateService.convert(amount, "USD", DATE);

        assertThat(result).contains(amount);
    }

    @Test
    void convert_rateAvailable_convertsAmount() {
        var amount = new MonetaryAmount(new BigDecimal("100.00"), "USD");
        var rate = new ExchangeRate("id", "USD", "BRL", new BigDecimal("5.50"), DATE, "MANUAL", Instant.now());
        when(exchangeRateRepository.findRate("USD", "BRL", DATE)).thenReturn(Optional.of(rate));

        var result = exchangeRateService.convert(amount, "BRL", DATE);

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("550.00"));
        assertThat(result.get().currency()).isEqualTo("BRL");
    }

    @Test
    void convert_noRateAvailable_returnsEmpty() {
        var amount = new MonetaryAmount(BigDecimal.TEN, "USD");
        when(exchangeRateRepository.findRate("USD", "BRL", DATE)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findRate("BRL", "USD", DATE)).thenReturn(Optional.empty());
        when(externalExchangeRateService.fetchRate("USD", "BRL", DATE)).thenReturn(Optional.empty());

        var result = exchangeRateService.convert(amount, "BRL", DATE);

        assertThat(result).isEmpty();
    }

    // --- saveRate tests ---

    @Test
    void saveRate_validRate_savesAndReturns() {
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = exchangeRateService.saveRate("USD", "BRL", new BigDecimal("5.50"), DATE, "MANUAL");

        assertThat(result.fromCurrency()).isEqualTo("USD");
        assertThat(result.toCurrency()).isEqualTo("BRL");
        assertThat(result.rate()).isEqualByComparingTo(new BigDecimal("5.500000"));
        assertThat(result.source()).isEqualTo("MANUAL");

        var captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository).save(captor.capture());
        assertThat(captor.getValue().id()).isNotNull();
    }

    @Test
    void saveRate_sameCurrency_throws() {
        assertThatThrownBy(() -> exchangeRateService.saveRate("USD", "USD", BigDecimal.ONE, DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");
    }

    @Test
    void saveRate_zerorate_throws() {
        assertThatThrownBy(() -> exchangeRateService.saveRate("USD", "BRL", BigDecimal.ZERO, DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void saveRate_negativeRate_throws() {
        assertThatThrownBy(() -> exchangeRateService.saveRate("USD", "BRL", new BigDecimal("-1.5"), DATE, "MANUAL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // --- getLatestRate tests ---

    @Test
    void getLatestRate_directRate_returnsRate() {
        var rate = new ExchangeRate("id", "USD", "BRL", new BigDecimal("5.50"), DATE, "MANUAL", Instant.now());
        when(exchangeRateRepository.findLatestRate("USD", "BRL")).thenReturn(Optional.of(rate));

        var result = exchangeRateService.getLatestRate("USD", "BRL");

        assertThat(result).contains(new BigDecimal("5.50"));
    }

    @Test
    void getLatestRate_inverseRate_returnsCalculatedInverse() {
        var rate = new ExchangeRate("id", "BRL", "USD", new BigDecimal("5.00"), DATE, "MANUAL", Instant.now());
        when(exchangeRateRepository.findLatestRate("USD", "BRL")).thenReturn(Optional.empty());
        when(exchangeRateRepository.findLatestRate("BRL", "USD")).thenReturn(Optional.of(rate));

        var result = exchangeRateService.getLatestRate("USD", "BRL");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("0.200000"));
    }

    @Test
    void getLatestRate_notFound_returnsEmpty() {
        when(exchangeRateRepository.findLatestRate("USD", "BRL")).thenReturn(Optional.empty());
        when(exchangeRateRepository.findLatestRate("BRL", "USD")).thenReturn(Optional.empty());

        var result = exchangeRateService.getLatestRate("USD", "BRL");

        assertThat(result).isEmpty();
    }
}
