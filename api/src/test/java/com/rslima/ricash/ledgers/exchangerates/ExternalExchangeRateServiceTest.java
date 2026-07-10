package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.configuration.ExchangeRateProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * Provider behavior of ExternalExchangeRateService against a mock HTTP server:
 * BCB happy path (direct and inverse), fallback to ExchangeRate-API, and
 * provider failures collapsing to Optional.empty().
 */
class ExternalExchangeRateServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 30);
    private static final String BCB_URL =
            "https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/CotacaoMoedaDia(moeda=@moeda,dataCotacao=@dataCotacao)?@moeda='{symbol}'&@dataCotacao='{date}'&$format=json";
    private static final String ER_API_URL = "https://open.er-api.com/v6/latest/{currency}";

    private MockRestServiceServer server;
    private ExternalExchangeRateService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ExternalExchangeRateService(builder.build(), new ExchangeRateProviderProperties(null, null));
    }

    @Test
    void usdToBrl_usesBcbQuote() {
        server.expect(requestToUriTemplate(BCB_URL, "USD", "30-01-2026"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"value\":[{\"cotacaoVenda\":5.75}]}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("USD", "BRL", DATE);

        assertThat(rate).contains(new BigDecimal("5.750000"));
        server.verify();
    }

    @Test
    void brlToUsd_invertsBcbQuote() {
        server.expect(requestToUriTemplate(BCB_URL, "USD", "30-01-2026"))
                .andRespond(withSuccess("{\"value\":[{\"cotacaoVenda\":5.75}]}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("BRL", "USD", DATE);

        assertThat(rate).contains(new BigDecimal("0.173913"));
    }

    @Test
    void bcbMiss_fallsBackToExchangeRateApi() {
        server.expect(requestToUriTemplate(BCB_URL, "EUR", "30-01-2026"))
                .andRespond(withSuccess("{\"value\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestToUriTemplate(ER_API_URL, "EUR"))
                .andRespond(withSuccess("{\"result\":\"success\",\"rates\":{\"BRL\":6.05}}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("EUR", "BRL", DATE);

        assertThat(rate).contains(new BigDecimal("6.050000"));
        server.verify();
    }

    @Test
    void nonBrlPair_goesStraightToExchangeRateApi() {
        server.expect(requestToUriTemplate(ER_API_URL, "USD"))
                .andRespond(withSuccess("{\"result\":\"success\",\"rates\":{\"EUR\":0.95}}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("USD", "EUR", DATE);

        assertThat(rate).contains(new BigDecimal("0.950000"));
    }

    @Test
    void providerErrors_collapseToEmpty() {
        server.expect(requestToUriTemplate(BCB_URL, "USD", "30-01-2026"))
                .andRespond(withServerError());
        server.expect(requestToUriTemplate(ER_API_URL, "USD"))
                .andRespond(withServerError());

        var rate = service.fetchRate("USD", "BRL", DATE);

        assertThat(rate).isEmpty();
    }

    @Test
    void erApiErrorResult_returnsEmpty() {
        server.expect(requestToUriTemplate(ER_API_URL, "USD"))
                .andRespond(withSuccess("{\"result\":\"error\",\"error-type\":\"invalid-key\"}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("USD", "EUR", DATE);

        assertThat(rate).isEmpty();
    }

    @Test
    void unsupportedBcbPair_skipsBcbEntirely() {
        // GBP/BRL: BCB only quotes USD and EUR, so only er-api is called.
        server.expect(requestToUriTemplate(ER_API_URL, "GBP"))
                .andRespond(withSuccess("{\"result\":\"success\",\"rates\":{\"BRL\":7.10}}", MediaType.APPLICATION_JSON));

        var rate = service.fetchRate("GBP", "BRL", DATE);

        assertThat(rate).contains(new BigDecimal("7.100000"));
        server.verify();
    }
}
