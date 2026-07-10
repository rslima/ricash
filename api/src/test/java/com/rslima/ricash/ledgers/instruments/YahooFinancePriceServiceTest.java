package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.InstrumentPriceProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

/**
 * Provider behavior of YahooFinancePriceService against a mock HTTP server:
 * ISIN-to-symbol resolution, currency-based listing selection (incl. rejecting
 * GBp pence quotes), chart-to-quote mapping in the exchange timezone, the
 * symbol cache, and provider failures collapsing to an empty list.
 */
class YahooFinancePriceServiceTest {

    private static final String SEARCH_URL =
            "https://query1.finance.yahoo.com/v1/finance/search?q={isin}&quotesCount=5&newsCount=0";
    private static final String CHART_LATEST_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=5d";
    private static final String CHART_BASE = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private static final String ISIN = "IE00B4L5Y983";
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    private MockRestServiceServer server;
    private YahooFinancePriceService service;
    private InstrumentPriceProviderProperties properties;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        properties = new InstrumentPriceProviderProperties(null, null, null, null);
        service = new YahooFinancePriceService(builder.build(), properties);
    }

    private static String searchJson(String... symbols) {
        return "{\"quotes\":[" + Arrays.stream(symbols)
                .map(symbol -> "{\"symbol\":\"" + symbol + "\",\"exchange\":\"AMS\",\"quoteType\":\"ETF\"}")
                .collect(Collectors.joining(",")) + "]}";
    }

    private static String chartJson(String currency, String timezone, String regularMarketPrice,
                                    long regularMarketTime, long[] timestamps, String[] closes) {
        String timestampJson = Arrays.stream(timestamps).mapToObj(Long::toString)
                .collect(Collectors.joining(","));
        String closeJson = String.join(",", closes);
        return """
                {"chart":{"result":[{
                  "meta":{"currency":"%s","exchangeTimezoneName":"%s",
                          "regularMarketPrice":%s,"regularMarketTime":%d},
                  "timestamp":[%s],
                  "indicators":{"quote":[{"close":[%s]}]}
                }]}}
                """.formatted(currency, timezone, regularMarketPrice, regularMarketTime, timestampJson, closeJson);
    }

    private static long epochAt(LocalDate date, int hour, ZoneId zone) {
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, 0)).atZone(zone).toEpochSecond();
    }

    @Test
    void latestFetch_resolvesIsinAndMapsQuotesInExchangeTimezone() {
        var closeDay1 = LocalDate.of(2026, 7, 8);
        var closeDay2 = LocalDate.of(2026, 7, 9);
        var tradingDay = LocalDate.of(2026, 7, 10);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.USER_AGENT, properties.userAgent()))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andExpect(method(GET))
                .andExpect(header(HttpHeaders.USER_AGENT, properties.userAgent()))
                .andRespond(withSuccess(chartJson("USD", "Europe/Amsterdam", "103.30",
                                epochAt(tradingDay, 12, AMSTERDAM),
                                new long[]{epochAt(closeDay1, 17, AMSTERDAM), epochAt(closeDay2, 17, AMSTERDAM)},
                                new String[]{"101.10", "102.20"}),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", null);

        assertThat(quotes).containsExactly(
                new YahooFinancePriceService.Quote(closeDay1, new BigDecimal("101.10")),
                new YahooFinancePriceService.Quote(closeDay2, new BigDecimal("102.20")),
                new YahooFinancePriceService.Quote(tradingDay, new BigDecimal("103.30")));
        server.verify();
    }

    @Test
    void currencyMismatch_triesNextSearchCandidate() {
        var day = LocalDate.of(2026, 7, 9);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.L", "IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.L"))
                .andRespond(withSuccess(chartJson("USD", "Europe/London", "80.00",
                                epochAt(day, 12, ZoneId.of("Europe/London")),
                                new long[]{}, new String[]{}),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chartJson("EUR", "Europe/Amsterdam", "88.00",
                                epochAt(day, 12, AMSTERDAM),
                                new long[]{}, new String[]{}),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "EUR", null);

        assertThat(quotes).containsExactly(
                new YahooFinancePriceService.Quote(day, new BigDecimal("88.00")));
        server.verify();
    }

    @Test
    void gbpPenceListing_isRejectedNeverConverted() {
        server.expect(requestToUriTemplate(SEARCH_URL, "GB00BH4HKS39"))
                .andRespond(withSuccess(searchJson("VWRL.L"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "VWRL.L"))
                .andRespond(withSuccess(chartJson("GBp", "Europe/London", "9500.00",
                                epochAt(LocalDate.of(2026, 7, 9), 12, ZoneId.of("Europe/London")),
                                new long[]{}, new String[]{}),
                        MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes("GB00BH4HKS39", "GBP", null)).isEmpty();
        server.verify();
    }

    @Test
    void lowercaseInstrumentCurrency_matchesMetaCurrency() {
        var day = LocalDate.of(2026, 7, 9);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chartJson("USD", "Europe/Amsterdam", "103.30",
                                epochAt(day, 12, AMSTERDAM),
                                new long[]{}, new String[]{}),
                        MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "usd", null)).hasSize(1);
    }

    @Test
    void nullCloses_areSkipped() {
        var day1 = LocalDate.of(2026, 7, 7);
        var holiday = LocalDate.of(2026, 7, 8);
        var day3 = LocalDate.of(2026, 7, 9);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chartJson("USD", "Europe/Amsterdam", "102.20",
                                epochAt(day3, 17, AMSTERDAM),
                                new long[]{epochAt(day1, 17, AMSTERDAM), epochAt(holiday, 17, AMSTERDAM),
                                        epochAt(day3, 17, AMSTERDAM)},
                                new String[]{"100.00", "null", "102.20"}),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", null);

        assertThat(quotes).extracting(YahooFinancePriceService.Quote::date)
                .containsExactly(day1, day3);
    }

    @Test
    void regularMarketPrice_overwritesSameDayClose() {
        var day = LocalDate.of(2026, 7, 9);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chartJson("USD", "Europe/Amsterdam", "102.99",
                                epochAt(day, 15, AMSTERDAM),
                                new long[]{epochAt(day, 9, AMSTERDAM)},
                                new String[]{"102.20"}),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", null);

        assertThat(quotes).containsExactly(
                new YahooFinancePriceService.Quote(day, new BigDecimal("102.99")));
    }

    @Test
    void searchRateLimited_returnsEmptyWithoutChartCalls() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withTooManyRequests());

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        server.verify();
    }

    @Test
    void allChartCandidatesFail_returnsEmpty() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.L", "IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.L"))
                .andRespond(withServerError());
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withTooManyRequests());

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        server.verify();
    }

    @Test
    void malformedJson_returnsEmpty() {
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
    }

    @Test
    void secondFetch_reusesCachedSymbolWithoutSearching() {
        var day = LocalDate.of(2026, 7, 9);
        String chart = chartJson("USD", "Europe/Amsterdam", "103.30",
                epochAt(day, 12, AMSTERDAM), new long[]{}, new String[]{});

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chart, MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chart, MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void staleCachedSymbol_isInvalidatedAndResearched() {
        var day = LocalDate.of(2026, 7, 9);

        // First fetch caches IWDA.L; second fetch finds it failing, re-searches
        // and resolves IWDA.AS instead.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.L"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.L"))
                .andRespond(withSuccess(chartJson("USD", "Europe/London", "80.00",
                                epochAt(day, 12, ZoneId.of("Europe/London")),
                                new long[]{}, new String[]{}),
                        MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.L"))
                .andRespond(withServerError());
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(chartJson("USD", "Europe/Amsterdam", "103.30",
                                epochAt(day, 12, AMSTERDAM),
                                new long[]{}, new String[]{}),
                        MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        assertThat(service.fetchQuotes(ISIN, "USD", null)).hasSize(1);
        server.verify();
    }

    @Test
    void backfill_usesPeriodParamsStartingOneDayBeforeFrom() {
        var from = LocalDate.of(2026, 6, 1);
        // One day early so exchanges ahead of UTC keep their from-date bar.
        long expectedPeriod1 = from.minusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        var day = LocalDate.of(2026, 6, 2);

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(CHART_BASE + "IWDA.AS")))
                .andExpect(header(HttpHeaders.USER_AGENT, properties.userAgent()))
                .andExpect(queryParam("interval", "1d"))
                .andExpect(queryParam("period1", String.valueOf(expectedPeriod1)))
                .andExpect(queryParam("period2", matchesPattern("\\d+")))
                .andRespond(withSuccess(chartJson("USD", "Europe/Amsterdam", "103.30",
                                epochAt(day, 12, AMSTERDAM),
                                new long[]{epochAt(day, 17, AMSTERDAM)},
                                new String[]{"101.10"}),
                        MediaType.APPLICATION_JSON));

        var quotes = service.fetchQuotes(ISIN, "USD", from);

        assertThat(quotes).hasSize(1);
        server.verify();
    }

    @Test
    void chartWithoutTimestampsOrIndicators_yieldsOnlyRegularMarketQuote() {
        // Symbols with no trades in the window omit timestamp/indicators data.
        var day = LocalDate.of(2026, 7, 9);
        String sparseChart = """
                {"chart":{"result":[{
                  "meta":{"currency":"USD","exchangeTimezoneName":"Europe/Amsterdam",
                          "regularMarketPrice":103.30,"regularMarketTime":%d}
                }]}}
                """.formatted(epochAt(day, 12, AMSTERDAM));

        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess(sparseChart, MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).containsExactly(
                new YahooFinancePriceService.Quote(day, new BigDecimal("103.30")));
    }

    @Test
    void chartWithNullResult_returnsEmpty() {
        // Yahoo error payloads carry result:null instead of an HTTP error status.
        server.expect(requestToUriTemplate(SEARCH_URL, ISIN))
                .andRespond(withSuccess(searchJson("IWDA.AS"), MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(CHART_LATEST_URL, "IWDA.AS"))
                .andRespond(withSuccess("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\"}}}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.fetchQuotes(ISIN, "USD", null)).isEmpty();
        server.verify();
    }
}
