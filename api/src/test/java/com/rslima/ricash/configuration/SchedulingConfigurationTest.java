package com.rslima.ricash.configuration;

import com.rslima.ricash.ledgers.instruments.InstrumentPriceRefreshScheduler;
import com.rslima.ricash.ledgers.instruments.InstrumentPriceService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The refresh-enabled flag gates the app's whole scheduling infrastructure:
 * present by default, absent when disabled (as done for all integration tests
 * via src/test/resources/application.properties).
 */
class SchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(InstrumentPriceService.class, () -> mock(InstrumentPriceService.class))
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test
    void schedulerRegisteredByDefault() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(InstrumentPriceRefreshScheduler.class));
    }

    @Test
    void refreshTask_isRegisteredWithDefaultCron() {
        contextRunner.run(context -> {
            var tasks = context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks();
            assertThat(tasks).singleElement().satisfies(task ->
                    assertThat(task.getTask()).isInstanceOfSatisfying(CronTask.class, cronTask ->
                            assertThat(cronTask.getExpression()).isEqualTo("0 30 6 * * *")));
        });
    }

    @Test
    void schedulerAbsentWhenRefreshDisabled() {
        contextRunner.withPropertyValues("ricash.instrument-prices.refresh-enabled=false")
                .run(context ->
                        assertThat(context).doesNotHaveBean(InstrumentPriceRefreshScheduler.class));
    }

    @Test
    void refreshDailyPrices_delegatesToService() {
        InstrumentPriceService instrumentPriceService = mock(InstrumentPriceService.class);
        when(instrumentPriceService.refreshAllActivePrices()).thenReturn(3);

        new InstrumentPriceRefreshScheduler(instrumentPriceService).refreshDailyPrices();

        verify(instrumentPriceService).refreshAllActivePrices();
    }
}
