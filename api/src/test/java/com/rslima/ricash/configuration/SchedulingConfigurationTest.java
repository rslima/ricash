package com.rslima.ricash.configuration;

import com.rslima.ricash.ledgers.exchangerates.ExchangeRateRefreshScheduler;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateService;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceRefreshScheduler;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each daily refresh scheduler is gated by its own refresh-enabled flag:
 * present by default, absent when disabled (as done for all integration tests
 * via src/test/resources/application.properties).
 */
class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(InstrumentPriceService.class, () -> mock(InstrumentPriceService.class))
            .withBean(ExchangeRateService.class, () -> mock(ExchangeRateService.class))
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void schedulersRegisteredByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(InstrumentPriceRefreshScheduler.class);
            assertThat(context).hasSingleBean(ExchangeRateRefreshScheduler.class);
        });
    }

    @Test
    void refreshTasks_areRegisteredWithDefaultCrons() {
        contextRunner.run(context -> {
            var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).extracting(ScheduledTask::getTask)
                    .allSatisfy(task -> assertThat(task).isInstanceOf(CronTask.class))
                    .extracting(task -> ((CronTask) task).getExpression())
                    .containsExactlyInAnyOrder("0 30 6 * * *", "0 0 18 * * *");
        });
    }

    @Test
    void priceSchedulerAbsentWhenRefreshDisabled() {
        contextRunner.withPropertyValues("ricash.instrument-prices.refresh-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(InstrumentPriceRefreshScheduler.class);
                    assertThat(context).hasSingleBean(ExchangeRateRefreshScheduler.class);
                });
    }

    @Test
    void rateSchedulerAbsentWhenRefreshDisabled() {
        contextRunner.withPropertyValues("ricash.exchange-rates.refresh-enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ExchangeRateRefreshScheduler.class);
                    assertThat(context).hasSingleBean(InstrumentPriceRefreshScheduler.class);
                });
    }

    @Test
    void refreshDailyPrices_delegatesToService() {
        InstrumentPriceService instrumentPriceService = mock(InstrumentPriceService.class);
        when(instrumentPriceService.refreshAllActivePrices()).thenReturn(3);

        new InstrumentPriceRefreshScheduler(instrumentPriceService).refreshDailyPrices();

        verify(instrumentPriceService).refreshAllActivePrices();
    }

    @Test
    void refreshDailyRates_delegatesToService() {
        ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
        when(exchangeRateService.refreshAllKnownRates()).thenReturn(2);

        new ExchangeRateRefreshScheduler(exchangeRateService).refreshDailyRates();

        verify(exchangeRateService).refreshAllKnownRates();
    }
}
