package com.rslima.ricash.ledgers.exchangerates;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Daily refresh of external exchange rates for every currency pair the
 * external provider has served before. Registered by
 * {@code SchedulingConfiguration}; disable via
 * {@code ricash.exchange-rates.refresh-enabled=false}. The schedule is pinned
 * to Brazil time regardless of server timezone: 18:00 BRT is safely after the
 * day's BCB PTAX closing bulletin (~13:10 BRT), so BRL pairs pick up PTAX
 * instead of falling back to the generic provider. The sweep stamps rows with
 * "today" in the same zone (see the Clock wired in LedgerConfiguration).
 */
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateRefreshScheduler {

    private final ExchangeRateService exchangeRateService;

    @Scheduled(cron = "${ricash.exchange-rates.refresh-cron:0 0 18 * * *}", zone = "America/Sao_Paulo")
    public void refreshDailyRates() {
        log.info("Starting scheduled exchange rate refresh");
        int refreshed = exchangeRateService.refreshAllKnownRates();
        log.info("Scheduled exchange rate refresh done: {} rate(s) refreshed", refreshed);
    }
}
