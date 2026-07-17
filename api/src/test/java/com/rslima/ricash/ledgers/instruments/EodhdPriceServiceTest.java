package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

/**
 * Provider behavior of EodhdPriceService against a mock HTTP server:
 * ISIN-to-ticker resolution via the search endpoint, per-row currency-based
 * listing selection (incl. rejecting GBX pence quotes), listing precedence
 * (exact ISIN before fuzzy, primary before secondary), EOD-bar-to-quote
 * mapping (raw close, not adjusted_close), the recent-window floor on the
 * from date, the per-ISIN-and-currency ticker cache, the missing-token guard,
 * and provider failures collapsing to an empty list.
 */
class EodhdPriceServiceTest {

    private static final String TOKEN = "test-token";
    private static final String SEARCH_URL =
            "https://eodhd.com/api/search/{isin}?api_token={token}&fmt=json";
    private static final String EOD_URL =
            "https://eodhd.com/api/eod/{ticker}?api_token={token}&fmt=json&from={from}";

    private static final String ISIN = "IE00B4L5Y983";

    /** Frozen clock: "today" is 2026-09-16, so the latest window starts 2026-09-06. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate LATEST_FROM = LocalDate.of(2026, 9, 6);

    private MockRestServiceServer server;
    private EodhdPriceService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new EodhdPriceService(builder.build(),
                new InstrumentPriceProviderProperties(null, TOKEN, null), FIXED_CLOCK);
    }

    /** One search row per "CODE|EXCHANGE|CURRENCY|ISIN|isPrimary" spec (last three may be "null"). */
    private static String searchJson(String... rows) {
        return "[" + Arrays.stream(rows).map(row -> {
            String[] p = row.split("\\|");
            return """
                    {"Code":"%s","Exchange":"%s","Name":"Some Fund","Type":"ETF","Country":"X",\
                    "Currency":%s,"ISIN":%s,"previousClose":101.5,"isPrimary":%s}"""
                    .formatted(p[0], p[1], quoteOrNull(p[2]), quoteOrNull(p[3]), p[4]);
        }).collect(Collectors.joining(",")) + "]";
    }

    private static String quoteOrNull(String value) {
        return "null".equals(value) ? "null" : "\"" + value + "\"";
    }

    /** One EOD bar per "date|close" spec; adjusted_close deliberately differs from close. */
    private static String eodJson(String... bars) {
        return "[" + Arrays.stream(bars).map(bar -> {
            String[] p = bar.split("\\|");
            return """
                    {"date":%s,"open":1.0,"high":2.0,"low":0.5,"close":%s,"adjusted_close":0.111111,"volume":123}"""
                    .formatted(quoteOrNull(p[0]), p[1]);
        }).collect(Collectors.joining(",")) + "]";
    }

    @Test
    void latestFetch_resolvesIsinAndMapsRawCloses() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andExpect(method(GET))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andExpect(method(GET))
                .andRespond(withSuccess(eodJson("2026-09-08|101.10", "2026-09-09|102.257"),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", null);

        // Raw close is used, never adjusted_close.
        assertThat(quotes).containsExactly(
                new EodhdPriceService.Quote(LocalDate.of(2026, 9, 8), new BigDecimal("101.10")),
                new EodhdPriceService.Quote(LocalDate.of(2026, 9, 9), new BigDecimal("102.257")));
        server.verify();
    }

    @Test
    void currencyIsCheckedPerRow_notPerExchange() {
        // LSE hosts both a USD (IOB) and a GBX line; the row, not the
        // exchange, decides which one matches.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "IWDA|LSE|GBX|" + ISIN + "|true",
                                "0R2V|LSE|USD|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "0R2V.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|88.00"), MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", null);

        assertThat(quotes).containsExactly(
                new EodhdPriceService.Quote(LocalDate.of(2026, 9, 9), new BigDecimal("88.00")));
        server.verify();
    }

    @Test
    void gbxPenceListing_isRejectedNeverConverted() {
        server.expect(requestToUriTemplate(SEARCH_URL, "GB00BH4HKS39", TOKEN))
                .andRespond(withSuccess(searchJson("VWRL|LSE|GBX|GB00BH4HKS39|true"),
                        MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes("GB00BH4HKS39", "GBP", null)).isEmpty();
        server.verify(); // no EOD call was made for the pence listing
    }

    @Test
    void primaryListing_isTriedFirst_nullIsPrimaryTolerated() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "IWDA|AS|USD|" + ISIN + "|null",
                                "IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void exactIsinRow_isPreferredOverFuzzyNullIsinRow() {
        // A fuzzy row without an ISIN is tolerated as a fallback, but must
        // never outrank a row that confirms the ISIN — even a primary one.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "MYST|US|USD|null|true",
                                "IWDA|LSE|USD|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void rowForDifferentIsin_isSkipped() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "OTHR|US|USD|US1111111111|true",
                                "IWDA|LSE|USD|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void nullCurrencyListing_isSkippedWithoutError() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "ODD|US|null|" + ISIN + "|true",
                                "IWDA|LSE|USD|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void lowercaseInstrumentCurrency_matchesListingCurrency() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "usd", null)).hasSize(1);
    }

    @Test
    void backfill_passesFromDateInclusive() {
        var from = LocalDate.of(2026, 6, 1);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, from))
                .andRespond(withSuccess(eodJson("2026-06-01|100.00", "2026-06-02|101.10"),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", from);

        assertThat(quotes).extracting(EodhdPriceService.Quote::date)
                .containsExactly(from, LocalDate.of(2026, 6, 2));
        server.verify();
    }

    @Test
    void backfillNearToday_stillRequestsTheRecentWindow() {
        // from = a recent Saturday would be a session-less range answered with
        // 200 []; the request is widened to the latest window so the caller
        // still gets the newest closes instead of a spurious "no price".
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", LocalDate.of(2026, 9, 11));

        assertThat(quotes).containsExactly(
                new EodhdPriceService.Quote(LocalDate.of(2026, 9, 9), new BigDecimal("103.30")));
        server.verify();
    }

    @Test
    void barsOutOfOrderOrMalformed_areSortedAndSkipped() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson(
                                "2026-09-09|102.20",
                                "2026-09-08|null",
                                "not-a-date|99.00",
                                "null|98.00",
                                "2026-09-07|100.00"),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", null);

        assertThat(quotes).containsExactly(
                new EodhdPriceService.Quote(LocalDate.of(2026, 9, 7), new BigDecimal("100.00")),
                new EodhdPriceService.Quote(LocalDate.of(2026, 9, 9), new BigDecimal("102.20")));
    }

    @Test
    void emptyEodForAllCandidates_returnsEmpty() {
        // 200 with [] is EODHD's legitimate empty-range answer, not an error.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "IWDA|LSE|USD|" + ISIN + "|true",
                                "IWDA|AS|USD|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.AS", TOKEN, LATEST_FROM))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        server.verify();
    }

    @Test
    void searchRateLimited_returnsEmptyWithoutEodCalls() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withTooManyRequests());

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        server.verify();
    }

    @Test
    void plainTextForbidden_collapsesToEmpty() {
        // EODHD 4xx bodies are plain text ("Forbidden"), not JSON.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.TEXT_HTML).body("Forbidden"));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        server.verify();
    }

    @Test
    void malformedJson_returnsEmpty() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
    }

    @Test
    void secondFetch_reusesCachedTickerWithoutSearching() {
        String eod = eodJson("2026-09-09|103.30");

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eod, MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eod, MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void cachedTicker_honorsBackfillFromDate() {
        // Prime the cache with a latest fetch, then backfill: the cached
        // ticker must be reused with the caller's from date, no new search.
        var from = LocalDate.of(2026, 6, 1);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, from))
                .andRespond(withSuccess(eodJson("2026-06-01|100.00"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "USD", from)).hasSize(1);
        server.verify();
    }

    @Test
    void cacheIsKeyedByIsinAndCurrency() {
        // The same ISIN fetched in another currency must not reuse the first
        // currency's ticker — that would persist wrong-currency prices.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "0R2V|LSE|USD|" + ISIN + "|true",
                                "IQQW|XETRA|EUR|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "0R2V.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson(
                                "0R2V|LSE|USD|" + ISIN + "|true",
                                "IQQW|XETRA|EUR|" + ISIN + "|false"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IQQW.XETRA", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|95.10"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "EUR", null)).hasSize(1);
        server.verify();
    }

    @Test
    void emptyRangeOnCachedTicker_returnsEmptyWithoutEvictingOrResearching() {
        // A legitimate 200 [] (e.g. suspended instrument) is a success: the
        // cached ticker stays valid and no search fan-out is triggered.
        String eod = eodJson("2026-09-09|103.30");

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eod, MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eod, MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1); // cache survived
        server.verify();
    }

    @Test
    void staleCachedTicker_isInvalidatedAndResearched() {
        // First fetch caches IWDA.LSE; a failed request (unlike an empty
        // range) marks it stale: re-search resolves IWDA.AS instead.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|LSE|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|103.30"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.LSE", TOKEN, LATEST_FROM))
                .andRespond(withServerError());
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN, TOKEN))
                .andRespond(withSuccess(searchJson("IWDA|AS|USD|" + ISIN + "|true"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(EOD_URL, "IWDA.AS", TOKEN, LATEST_FROM))
                .andRespond(withSuccess(eodJson("2026-09-09|104.10"), MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void missingApiToken_skipsProviderEntirely() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer tokenlessServer = MockRestServiceServer.bindTo(builder).build();
        EodhdPriceService tokenless = new EodhdPriceService(builder.build(),
                new InstrumentPriceProviderProperties(null, null, null), FIXED_CLOCK);

        assertThat(tokenless.fetchQuotes(ISIN, "USD", null)).isEmpty();
        tokenlessServer.verify(); // no HTTP call was attempted
    }
}
