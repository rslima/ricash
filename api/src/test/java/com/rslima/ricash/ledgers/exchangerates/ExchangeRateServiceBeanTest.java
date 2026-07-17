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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

    /**
     * Frozen clock: "today" for the scheduled sweep is 2026-09-16 — a date
     * deliberately distinct from any plausible test-run date, so a regression
     * to LocalDate.now() cannot silently pass.
     */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateServiceBean(exchangeRateRepository, externalExchangeRateService,
                FIXED_CLOCK);
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

    // --- refreshRate tests ---

    @Test
    void refreshRate_fetchesFromExternalAndSaves_bypassingDatabaseCache() {
        when(externalExchangeRateService.fetchRate("USD", "BRL", DATE)).thenReturn(Optional.of(new BigDecimal("5.75")));
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = exchangeRateService.refreshRate("USD", "BRL", DATE);

        assertThat(result).isPresent();
        assertThat(result.get().rate()).isEqualByComparingTo(new BigDecimal("5.75"));
        assertThat(result.get().source()).isEqualTo("EXTERNAL_API");

        // Force-refresh must not consult the database cache first.
        verify(exchangeRateRepository, never()).findRate(any(), any(), any());
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }

    @Test
    void refreshRate_normalizesCurrencyCasing() {
        when(externalExchangeRateService.fetchRate("USD", "BRL", DATE)).thenReturn(Optional.of(new BigDecimal("5.75")));
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = exchangeRateService.refreshRate("usd", "brl", DATE);

        assertThat(result).isPresent();
        assertThat(result.get().fromCurrency()).isEqualTo("USD");
        assertThat(result.get().toCurrency()).isEqualTo("BRL");
    }

    @Test
    void refreshRate_externalHasNoRate_returnsEmptyAndDoesNotSave() {
        when(externalExchangeRateService.fetchRate("USD", "BRL", DATE)).thenReturn(Optional.empty());

        var result = exchangeRateService.refreshRate("USD", "BRL", DATE);

        assertThat(result).isEmpty();
        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    void refreshRate_sameCurrency_throws() {
        assertThatThrownBy(() -> exchangeRateService.refreshRate("USD", "USD", DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");
        verifyNoInteractions(externalExchangeRateService);
    }

    // --- refreshAllKnownRates tests ---
    // Unstubbed findRate calls return Optional.empty(), i.e. "no rate stored
    // for today yet" — the common case for the manual-rate guard.

    @Test
    void refreshAllKnownRates_refreshesEachExternalPairForToday() {
        when(exchangeRateRepository.findDistinctExternalPairs()).thenReturn(List.of(
                new CurrencyPair("USD", "BRL"),
                new CurrencyPair("EUR", "BRL")));
        when(externalExchangeRateService.fetchRate("USD", "BRL", TODAY)).thenReturn(Optional.of(new BigDecimal("5.75")));
        when(externalExchangeRateService.fetchRate("EUR", "BRL", TODAY)).thenReturn(Optional.of(new BigDecimal("6.10")));
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(exchangeRateService.refreshAllKnownRates()).isEqualTo(2);

        var captor = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(exchangeRateRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(saved -> {
            assertThat(saved.effectiveDate()).isEqualTo(TODAY);
            assertThat(saved.source()).isEqualTo("EXTERNAL_API");
        });
    }

    @Test
    void refreshAllKnownRates_skipsPairWhoseTodayRateIsManual() {
        when(exchangeRateRepository.findDistinctExternalPairs()).thenReturn(List.of(
                new CurrencyPair("USD", "BRL")));
        when(exchangeRateRepository.findRate("USD", "BRL", TODAY)).thenReturn(Optional.of(
                new ExchangeRate("id", "USD", "BRL", new BigDecimal("5.60"), TODAY, "MANUAL", Instant.now())));

        assertThat(exchangeRateService.refreshAllKnownRates()).isZero();

        // The user's rate for today must survive the sweep untouched.
        verifyNoInteractions(externalExchangeRateService);
        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    void refreshAllKnownRates_staleManualRate_doesNotBlockRefresh() {
        // Only a manual rate FOR TODAY wins; an older manual row means the
        // pair still needs today's external value.
        when(exchangeRateRepository.findDistinctExternalPairs()).thenReturn(List.of(
                new CurrencyPair("USD", "BRL")));
        when(exchangeRateRepository.findRate("USD", "BRL", TODAY)).thenReturn(Optional.of(
                new ExchangeRate("id", "USD", "BRL", new BigDecimal("5.60"), TODAY.minusDays(3), "MANUAL", Instant.now())));
        when(externalExchangeRateService.fetchRate("USD", "BRL", TODAY)).thenReturn(Optional.of(new BigDecimal("5.75")));
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(exchangeRateService.refreshAllKnownRates()).isEqualTo(1);
    }

    @Test
    void refreshAllKnownRates_continuesAfterSinglePairFailure() {
        when(exchangeRateRepository.findDistinctExternalPairs()).thenReturn(List.of(
                new CurrencyPair("USD", "BRL"),
                new CurrencyPair("EUR", "BRL")));
        when(externalExchangeRateService.fetchRate("USD", "BRL", TODAY))
                .thenThrow(new IllegalStateException("boom"));
        when(externalExchangeRateService.fetchRate("EUR", "BRL", TODAY)).thenReturn(Optional.of(new BigDecimal("6.10")));
        when(exchangeRateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(exchangeRateService.refreshAllKnownRates()).isEqualTo(1);
    }

    @Test
    void refreshAllKnownRates_pairWithoutExternalRate_isNotCounted() {
        when(exchangeRateRepository.findDistinctExternalPairs()).thenReturn(List.of(
                new CurrencyPair("USD", "XXX")));
        when(externalExchangeRateService.fetchRate("USD", "XXX", TODAY)).thenReturn(Optional.empty());

        assertThat(exchangeRateService.refreshAllKnownRates()).isZero();
        verify(exchangeRateRepository, never()).save(any());
    }

    @Test
    void refreshAllKnownRates_noStoredPairs_returnsZeroWithoutFetching() {
        when(exchangeRateRepository.findDistinctExternalPairs()).thenReturn(List.of());

        assertThat(exchangeRateService.refreshAllKnownRates()).isZero();
        verifyNoInteractions(externalExchangeRateService);
    }
}
