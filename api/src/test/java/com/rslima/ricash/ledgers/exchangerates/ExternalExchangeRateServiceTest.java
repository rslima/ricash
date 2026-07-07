package com.rslima.ricash.ledgers.exchangerates;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalExchangeRateServiceTest {

    private MockRestServiceServer server;
    private ExternalExchangeRateService service;

    private static final LocalDate DATE = LocalDate.of(2026, 1, 30);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ExternalExchangeRateService(
                builder,
                new ObjectMapper(),
                new ExchangeRateProviderProperties("https://bcb.test", "https://erapi.test"));
    }

    @Test
    void fetchRate_usdToBrl_usesBcbRate() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://bcb.test/CotacaoMoedaDia")))
                .andRespond(withSuccess("{\"value\":[{\"cotacaoVenda\":5.4321}]}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("USD", "BRL", DATE);

        assertThat(rate).isPresent();
        assertThat(rate.get()).isEqualByComparingTo("5.432100");
    }

    @Test
    void fetchRate_bcbFailure_fallsBackToExchangeRateApi() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://bcb.test/")))
                .andRespond(withServerError());
        server.expect(requestTo("https://erapi.test/latest/USD"))
                .andRespond(withSuccess("{\"result\":\"success\",\"rates\":{\"BRL\":5.1}}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("USD", "BRL", DATE);

        assertThat(rate).isPresent();
        assertThat(rate.get()).isEqualByComparingTo("5.100000");
    }

    @Test
    void fetchRate_malformedBody_returnsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://erapi.test/latest/")))
                .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("EUR", "USD", DATE);

        assertThat(rate).isEmpty();
    }

    @Test
    void fetchRate_apiError_returnsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://erapi.test/latest/")))
                .andRespond(withSuccess("{\"result\":\"error\",\"error-type\":\"quota\"}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("EUR", "USD", DATE);

        assertThat(rate).isEmpty();
    }

    @Test
    void fetchRate_serverError_returnsEmpty() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://erapi.test/latest/")))
                .andRespond(withServerError());

        var rate = service.fetchRate("EUR", "USD", DATE);

        assertThat(rate).isEmpty();
    }
}
