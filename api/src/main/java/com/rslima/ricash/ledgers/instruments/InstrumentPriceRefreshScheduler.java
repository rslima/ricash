package com.rslima.ricash.ledgers.instruments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Daily refresh of external prices for every ACTIVE instrument with an ISIN.
 * Registered (and scheduling enabled) by {@code SchedulingConfiguration};
 * disable via {@code ricash.instrument-prices.refresh-enabled=false}.
 */
@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceRefreshScheduler {

    private final InstrumentPriceService instrumentPriceService;

    @Scheduled(cron = "${ricash.instrument-prices.refresh-cron:0 30 6 * * *}")
    public void refreshDailyPrices() {
        log.info("Starting scheduled instrument price refresh");
        int refreshed = instrumentPriceService.refreshAllActivePrices();
        log.info("Scheduled instrument price refresh done: {} instrument(s) refreshed", refreshed);
    }
}
