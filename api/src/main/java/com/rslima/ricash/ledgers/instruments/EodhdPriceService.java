package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for fetching instrument prices from the EODHD API (eodhd.com),
 * looked up by ISIN. Resolves the ISIN to a listing via the search endpoint,
 * then reads end-of-day closes from the EOD endpoint as
 * {@code /eod/{Code}.{Exchange}}.
 *
 * <p>One ISIN maps to many listings quoting in different currencies (including
 * minor-unit pseudo-currencies like GBX pence or ZAC cents), and the EOD
 * endpoint carries no currency metadata, so listings are filtered by the
 * search row's {@code Currency} exactly matching the instrument's currency —
 * pence quotes are rejected, never converted.
 *
 * <p>EOD rows use raw {@code close} (not the split/dividend-rewritten
 * {@code adjusted_close}) and carry the exchange-local trading date as a plain
 * {@code YYYY-MM-DD} string, so no timezone handling is needed. Requests always
 * span at least the recent window so callers get the latest close even when the
 * requested backfill range holds no trading session (extra bars just re-upsert).
 * A resolved ticker is cached per ISIN and currency; with a warm cache a
 * refresh costs one API call per instrument.
 */
@RequiredArgsConstructor
@Slf4j
public class EodhdPriceService {

    /** Covers weekends and holiday runs so a latest-close fetch still spans several sessions. */
    private static final int LATEST_WINDOW_DAYS = 10;

    private final RestClient restClient;
    private final InstrumentPriceProviderProperties properties;
    private final Clock clock;

    /** ISIN|CURRENCY -> EODHD ticker (Code.Exchange) that last returned prices. */
    private final ConcurrentHashMap<String, String> tickerCache = new ConcurrentHashMap<>();

    /** A single end-of-day price point. */
    public record Quote(LocalDate date, BigDecimal price) {
    }

    /**
     * EODHD search row (bare JSON array response). Identity fields are
     * PascalCase on the wire; {@code isPrimary} may be null.
     * {"Code":"AAPL","Exchange":"US","Currency":"USD","ISIN":"US0378331005","isPrimary":true,...}
     */
    record SearchListing(
            @JsonProperty("Code") String code,
            @JsonProperty("Exchange") String exchange,
            @JsonProperty("Currency") String currency,
            @JsonProperty("ISIN") String isin,
            @JsonProperty("isPrimary") Boolean isPrimary
    ) {
    }

    /**
     * EODHD EOD bar (bare JSON array response, ascending by date):
     * {"date":"2026-07-15","open":317.625,...,"close":327.5,"adjusted_close":327.5,"volume":60780931}
     */
    record EodBar(String date, BigDecimal close) {
    }

    /**
     * Fetches EOD closes for the ISIN's listing whose quote currency matches,
     * in chronological order.
     *
     * @param isin the instrument's ISIN
     * @param currency the instrument's currency; listings quoting in any other
     *        currency (including GBX/ZAC minor units) are skipped
     * @param from backfill start date (inclusive), or null for just the recent
     *        days needed to pick up the latest close
     * @return chronological quotes; empty when the ISIN is unresolvable, the
     *         provider is unavailable, or no listing matches the currency
     */
    public List<Quote> fetchQuotes(String isin, String currency, LocalDate from) {
        if (apiToken().isBlank()) {
            log.warn("No EODHD API token configured (ricash.instrument-prices.api-token); skipping fetch for ISIN {}", isin);
            return List.of();
        }

        final String wantedCurrency = currency.toUpperCase(Locale.ROOT);
        final String cacheKey = isin + "|" + wantedCurrency;

        String cachedTicker = tickerCache.get(cacheKey);
        if (cachedTicker != null) {
            Optional<List<Quote>> result = fetchEod(cachedTicker, from);
            if (result.isPresent()) {
                // Success keeps the cache even when the range is legitimately
                // empty (200 []); only a failed request suggests a stale ticker.
                return result.get();
            }
            tickerCache.remove(cacheKey, cachedTicker);
        }

        for (String ticker : resolveTickers(isin, wantedCurrency)) {
            List<Quote> quotes = fetchEod(ticker, from).orElse(List.of());
            if (!quotes.isEmpty()) {
                tickerCache.put(cacheKey, ticker);
                log.info("Resolved ISIN {} to EODHD ticker {} ({} quote(s))", isin, ticker, quotes.size());
                return quotes;
            }
        }

        log.warn("No usable EODHD listing for ISIN {} in currency {}", isin, wantedCurrency);
        return List.of();
    }

    /**
     * Search returns every exchange listing sharing the ISIN; keep the ones in
     * the wanted currency (checked per row — e.g. LSE hosts both USD and GBX
     * lines). Rows confirming the ISIN come before fuzzy rows without one, and
     * the primary listing comes first within each group.
     */
    private List<String> resolveTickers(String isin, String wantedCurrency) {
        List<SearchListing> listings = search(isin);

        List<String> tickers = listings.stream()
                .filter(listing -> isPresent(listing.code()) && isPresent(listing.exchange()))
                // The search is fuzzy; drop rows for other instruments outright.
                .filter(listing -> listing.isin() == null || listing.isin().equalsIgnoreCase(isin))
                .filter(listing -> currencyMatches(listing, wantedCurrency))
                .sorted(Comparator.comparing((SearchListing listing) -> listing.isin() == null)
                        .thenComparing(listing -> !Boolean.TRUE.equals(listing.isPrimary())))
                .map(listing -> listing.code() + "." + listing.exchange())
                .toList();

        if (tickers.isEmpty() && !listings.isEmpty()) {
            log.warn("ISIN {} has {} EODHD listing(s) but none quotes in {} (currencies seen: {})",
                    isin, listings.size(), wantedCurrency,
                    listings.stream().map(SearchListing::currency).distinct().toList());
        }
        return tickers;
    }

    private List<SearchListing> search(String isin) {
        try {
            List<SearchListing> listings = restClient.get()
                    .uri(properties.baseUrl() + "/search/{isin}?api_token={token}&fmt=json", isin, apiToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<SearchListing>>() {
                    });

            if (listings == null || listings.isEmpty()) {
                log.debug("No EODHD search results for ISIN {}", isin);
                return List.of();
            }
            return listings;

        } catch (RuntimeException e) {
            // Broad on purpose: bad configuration (e.g. a malformed base URL)
            // must collapse to "unavailable" without the raw message — which
            // can embed the request URL and token — escaping to callers.
            log.warn("Failed to search EODHD for ISIN {}: {}", isin, sanitize(e.getMessage()));
            return List.of();
        }
    }

    /**
     * Fetches the EOD bars for one ticker. An empty {@link Optional} is a
     * failed request (transport, HTTP error, parse); a present-but-empty list
     * is EODHD's legitimate answer for a range without a trading session.
     */
    private Optional<List<Quote>> fetchEod(String ticker, LocalDate from) {
        // Always span at least the recent window: EODHD answers 200 [] for
        // ranges without a session (from == today, weekend backfills), and
        // without `from` it returns decades of rows. Bars before the caller's
        // date are true data and simply re-upsert.
        LocalDate windowStart = LocalDate.now(clock).minusDays(LATEST_WINDOW_DAYS);
        LocalDate effectiveFrom = from == null || from.isAfter(windowStart) ? windowStart : from;
        try {
            List<EodBar> bars = restClient.get()
                    .uri(properties.baseUrl() + "/eod/{ticker}?api_token={token}&fmt=json&from={from}",
                            ticker, apiToken(), effectiveFrom)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EodBar>>() {
                    });

            if (bars == null) {
                log.debug("No response body from EODHD for ticker {}", ticker);
                return Optional.empty();
            }
            return Optional.of(toQuotes(bars, ticker));

        } catch (RuntimeException e) {
            // Broad on purpose — see search().
            log.warn("Failed to fetch EODHD EOD data for ticker {}: {}", ticker, sanitize(e.getMessage()));
            return Optional.empty();
        }
    }

    private boolean currencyMatches(SearchListing listing, String wantedCurrency) {
        // Exact comparison on purpose: minor-unit pseudo-currencies ("GBX"
        // pence, "ZAC" cents) must never pass as their major unit.
        if (wantedCurrency.equals(listing.currency())) {
            return true;
        }
        log.debug("EODHD listing {}.{} quotes in {} but instrument currency is {}; skipping",
                listing.code(), listing.exchange(), listing.currency(), wantedCurrency);
        return false;
    }

    private List<Quote> toQuotes(List<EodBar> bars, String ticker) {
        TreeMap<LocalDate, BigDecimal> pricesByDate = new TreeMap<>();
        for (EodBar bar : bars) {
            if (bar == null || bar.date() == null || bar.close() == null) {
                continue;
            }
            try {
                pricesByDate.put(LocalDate.parse(bar.date()), bar.close());
            } catch (DateTimeParseException e) {
                log.debug("Skipping EODHD bar with unparseable date {} for ticker {}", bar.date(), ticker);
            }
        }
        return pricesByDate.entrySet().stream()
                .map(entry -> new Quote(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String apiToken() {
        return properties.apiToken() == null ? "" : properties.apiToken();
    }

    /** Provider error messages can echo the request URL; keep the token out of logs. */
    private String sanitize(String message) {
        String token = apiToken();
        if (message == null || token.isBlank()) {
            return message;
        }
        return message.replace(token, "***");
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
