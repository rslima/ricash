package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.configuration.SecurityConfiguration;
import com.rslima.ricash.testsupport.WebTestConfiguration;
import com.toedter.spring.hateoas.jsonapi.JsonApiMediaTypeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExchangeRateController.class)
@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)
@Import({WebTestConfiguration.class, SecurityConfiguration.class, ExchangeRateMapperImpl.class})
class ExchangeRateControllerTest {

    private static final String USER = "user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeRateService exchangeRateService;

    private ExchangeRate testRate() {
        return new ExchangeRate("rate-1", "USD", "BRL", new BigDecimal("5.750000"),
                LocalDate.of(2026, 1, 30), "MANUAL", Instant.now());
    }

    @Test
    void createExchangeRate_routesThroughService() throws Exception {
        when(exchangeRateService.saveManualRate(eq("usd"), eq("brl"), any(), eq(LocalDate.of(2026, 1, 30))))
                .thenReturn(testRate());

        mockMvc.perform(post("/v1/exchange-rates")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"fromCurrency\":\"usd\",\"toCurrency\":\"brl\",\"rate\":5.75,\"effectiveDate\":\"2026-01-30\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", is("rate-1")))
                .andExpect(jsonPath("$.data.attributes.fromCurrency", is("USD")))
                .andExpect(jsonPath("$.data.attributes.toCurrency", is("BRL")));
    }

    @Test
    void fetchExchangeRate_notAvailable_returns404() throws Exception {
        when(exchangeRateService.refreshRate(any(), any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/exchange-rates/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"fromCurrency\":\"USD\",\"toCurrency\":\"BRL\",\"date\":\"2026-01-30\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));
    }

    @Test
    void deleteExchangeRate_routesThroughService() throws Exception {
        mockMvc.perform(delete("/v1/exchange-rates/rate-1")
                        .with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isNoContent());

        verify(exchangeRateService).deleteRate("rate-1");
    }

    @Test
    void listExchangeRates_returnsPagedRates() throws Exception {
        when(exchangeRateService.listRates(any()))
                .thenReturn(new PageImpl<>(List.of(testRate()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/exchange-rates")
                        .with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.meta.page.totalElements", is(1)));
    }
}
