package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fetching instrument prices from Yahoo Finance's unofficial API,
 * looked up by ISIN. Resolves the ISIN to a listing via the search endpoint,
 * then reads end-of-day closes (and the latest regular-market price) from the
 * chart endpoint.
 *
 * <p>One ISIN maps to many listings quoting in different currencies (including
 * minor-unit pseudo-currencies like GBp pence), so only a listing whose chart
 * {@code meta.currency} exactly matches the instrument's currency is accepted.
 */
@RequiredArgsConstructor
@Slf4j
public class YahooFinancePriceService {

    private final RestClient restClient;
    private final InstrumentPriceProviderProperties properties;

    /** ISIN|CURRENCY -> Yahoo symbol that last passed the currency check. */
    private final ConcurrentHashMap<String, String> symbolCache = new ConcurrentHashMap<>();

    /** A single end-of-day (or latest regular-market) price point. */
    public record Quote(LocalDate date, BigDecimal price) {
    }

    /** Yahoo search response: {"quotes":[{"symbol":"IWDA.L","exchange":"LSE",...}]} */
    record SearchResponse(List<SearchQuote> quotes) {
    }

    record SearchQuote(String symbol) {
    }

    /**
     * Yahoo chart response:
     * {"chart":{"result":[{"meta":{"currency":"USD","regularMarketPrice":101.5,
     * "regularMarketTime":1751971800,"exchangeTimezoneName":"Europe/London"},
     * "timestamp":[...],"indicators":{"quote":[{"close":[...]}]}}]}}
     */
    record ChartResponse(Chart chart) {
    }

    record Chart(List<ChartResult> result) {
    }

    record ChartResult(ChartMeta meta, List<Long> timestamp, ChartIndicators indicators) {
    }

    record ChartMeta(String currency, BigDecimal regularMarketPrice, Long regularMarketTime,
                     String exchangeTimezoneName) {
    }

    record ChartIndicators(List<ChartQuote> quote) {
    }

    record ChartQuote(List<BigDecimal> close) {
    }

    /**
     * Fetches EOD closes (plus the latest regular-market price) for the ISIN's
     * listing whose quote currency matches, in chronological order.
     *
     * @param isin the instrument's ISIN
     * @param currency the instrument's currency; listings quoting in any other
     *        currency (including GBp/ZAc minor units) are skipped
     * @param from backfill start date, or null for just the recent days needed
     *        to pick up the latest close
     * @return chronological quotes; empty when the ISIN is unresolvable, the
     *         provider is unavailable, or no listing matches the currency
     */
    public List<Quote> fetchQuotes(String isin, String currency, LocalDate from) {
        final String wantedCurrency = currency.toUpperCase(Locale.ROOT);
        final String cacheKey = isin + "|" + wantedCurrency;

        String cachedSymbol = symbolCache.get(cacheKey);
        if (cachedSymbol != null) {
            List<Quote> quotes = fetchChart(cachedSymbol, from)
                    .filter(chart -> currencyMatches(chart, wantedCurrency, cachedSymbol))
                    .map(this::toQuotes)
                    .orElse(List.of());
            if (!quotes.isEmpty()) {
                return quotes;
            }
            symbolCache.remove(cacheKey, cachedSymbol);
        }

        for (String symbol : searchSymbols(isin)) {
            List<Quote> quotes = fetchChart(symbol, from)
                    .filter(chart -> currencyMatches(chart, wantedCurrency, symbol))
                    .map(this::toQuotes)
                    .orElse(List.of());
            if (!quotes.isEmpty()) {
                symbolCache.put(cacheKey, symbol);
                log.info("Resolved ISIN {} to Yahoo symbol {} ({} quote(s))", isin, symbol, quotes.size());
                return quotes;
            }
        }

        log.warn("No usable Yahoo Finance listing for ISIN {} in currency {}", isin, wantedCurrency);
        return List.of();
    }

    private List<String> searchSymbols(String isin) {
        try {
            SearchResponse response = restClient.get()
                .uri(properties.searchBaseUrl() + "?q={isin}&quotesCount=5&newsCount=0", isin)
                .header(HttpHeaders.USER_AGENT, properties.userAgent())
                .retrieve()
                .body(SearchResponse.class);

            if (response == null || response.quotes() == null) {
                log.debug("No Yahoo Finance search results for ISIN {}", isin);
                return List.of();
            }

            return response.quotes().stream()
                .map(SearchQuote::symbol)
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .toList();

        } catch (RestClientException | JacksonException e) {
            log.warn("Failed to search Yahoo Finance for ISIN {}: {}", isin, e.getMessage());
            return List.of();
        }
    }

    private Optional<ChartResult> fetchChart(String symbol, LocalDate from) {
        try {
            final ChartResponse response;
            if (from == null) {
                // 5 days covers the latest close across weekends and holidays
                response = restClient.get()
                    .uri(properties.chartBaseUrl() + "/{symbol}?interval=1d&range=5d", symbol)
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .retrieve()
                    .body(ChartResponse.class);
            } else {
                // Yahoo stamps 1d bars at the exchange-local session open and
                // filters by timestamp >= period1, so UTC midnight of `from`
                // would drop the from-date bar on exchanges ahead of UTC (and
                // put period1 after period2 for from = today). Starting a day
                // early is harmless: the extra bar just re-upserts.
                response = restClient.get()
                    .uri(properties.chartBaseUrl() + "/{symbol}?interval=1d&period1={period1}&period2={period2}",
                        symbol,
                        from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                        Instant.now().getEpochSecond())
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .retrieve()
                    .body(ChartResponse.class);
            }

            if (response == null || response.chart() == null
                    || response.chart().result() == null || response.chart().result().isEmpty()) {
                log.debug("No chart data from Yahoo Finance for symbol {}", symbol);
                return Optional.empty();
            }

            return Optional.ofNullable(response.chart().result().getFirst());

        } catch (RestClientException | JacksonException e) {
            log.warn("Failed to fetch Yahoo Finance chart for symbol {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private boolean currencyMatches(ChartResult chart, String wantedCurrency, String symbol) {
        String metaCurrency = chart.meta() != null ? chart.meta().currency() : null;

        // Exact comparison on purpose: minor-unit pseudo-currencies ("GBp" pence,
        // "ZAc" cents) must never pass as their major unit.
        if (wantedCurrency.equals(metaCurrency)) {
            return true;
        }

        log.warn("Yahoo listing {} quotes in {} but instrument currency is {}; skipping",
            symbol, metaCurrency, wantedCurrency);
        return false;
    }

    private List<Quote> toQuotes(ChartResult chart) {
        ZoneId exchangeZone = exchangeZone(chart.meta());
        TreeMap<LocalDate, BigDecimal> pricesByDate = new TreeMap<>();

        List<Long> timestamps = chart.timestamp() != null ? chart.timestamp() : List.of();
        List<BigDecimal> closes = closes(chart.indicators());

        for (int i = 0; i < Math.min(timestamps.size(), closes.size()); i++) {
            Long timestamp = timestamps.get(i);
            BigDecimal close = closes.get(i);
            if (timestamp == null || close == null) {
                continue; // holidays surface as null closes
            }
            pricesByDate.put(dateOf(timestamp, exchangeZone), close);
        }

        // The regular-market price is the freshest value; keyed by its own trading
        // date it overwrites a same-day close (and gets corrected by the final
        // close on the next fetch via the repository upsert).
        ChartMeta meta = chart.meta();
        if (meta != null && meta.regularMarketPrice() != null && meta.regularMarketTime() != null) {
            pricesByDate.put(dateOf(meta.regularMarketTime(), exchangeZone), meta.regularMarketPrice());
        }

        return pricesByDate.entrySet().stream()
            .map(entry -> new Quote(entry.getKey(), entry.getValue()))
            .toList();
    }

    private List<BigDecimal> closes(ChartIndicators indicators) {
        if (indicators == null || indicators.quote() == null || indicators.quote().isEmpty()) {
            return List.of();
        }
        ChartQuote quote = indicators.quote().getFirst();
        return quote != null && quote.close() != null ? quote.close() : List.of();
    }

    private ZoneId exchangeZone(ChartMeta meta) {
        if (meta == null || meta.exchangeTimezoneName() == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(meta.exchangeTimezoneName());
        } catch (DateTimeException e) {
            log.debug("Unknown exchange timezone {}; falling back to UTC", meta.exchangeTimezoneName());
            return ZoneOffset.UTC;
        }
    }

    private LocalDate dateOf(long epochSeconds, ZoneId zone) {
        return Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate();
    }
}
