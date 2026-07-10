package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.configuration.SecurityConfiguration;
import com.rslima.ricash.testsupport.WebTestConfiguration;
import com.toedter.spring.hateoas.jsonapi.JsonApiMediaTypeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InstrumentPriceController.class)
@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)
@Import({WebTestConfiguration.class, SecurityConfiguration.class, InstrumentPriceMapperImpl.class})
class InstrumentPriceControllerTest {

    private static final String USER = "user-1";
    private static final String LEDGER_SLUG = "main";
    private static final String INSTRUMENT_ID = "instr-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstrumentPriceService instrumentPriceService;

    @MockitoBean
    private InstrumentService instrumentService;

    private Instrument testInstrument() {
        return new Instrument(INSTRUMENT_ID, "ledger-1", "IWDA", "iShares Core MSCI World",
                InstrumentType.ETF, "USD", "AMS", "IE00B4L5Y983", InstrumentStatus.ACTIVE, Instant.now());
    }

    private InstrumentPrice testPrice() {
        return new InstrumentPrice("price-1", INSTRUMENT_ID, new BigDecimal("103.300000"),
                LocalDate.of(2026, 7, 9), "YAHOO", Instant.now());
    }

    @Test
    void fetchPrices_returnsLatestUpsertedPrice() throws Exception {
        when(instrumentService.find(USER, LEDGER_SLUG, INSTRUMENT_ID)).thenReturn(Optional.of(testInstrument()));
        when(instrumentPriceService.fetchPrices(eq(USER), eq(LEDGER_SLUG), eq(INSTRUMENT_ID),
                eq(LocalDate.of(2026, 6, 1)))).thenReturn(testPrice());

        mockMvc.perform(post("/v1/ledgers/main/instrument-prices/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"instrumentId\":\"instr-1\",\"from\":\"2026-06-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", is("price-1")))
                .andExpect(jsonPath("$.data.attributes.source", is("YAHOO")))
                .andExpect(jsonPath("$.data.attributes.instrumentSymbol", is("IWDA")))
                .andExpect(jsonPath("$.data.attributes.effectiveDate", is("2026-07-09")));

        verify(instrumentPriceService).fetchPrices(USER, LEDGER_SLUG, INSTRUMENT_ID, LocalDate.of(2026, 6, 1));
    }

    @Test
    void fetchPrices_unknownInstrument_returns404() throws Exception {
        when(instrumentService.find(USER, LEDGER_SLUG, INSTRUMENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/ledgers/main/instrument-prices/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"instrumentId\":\"instr-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));
    }

    @Test
    void fetchPrices_providerHasNothing_returns404() throws Exception {
        when(instrumentService.find(USER, LEDGER_SLUG, INSTRUMENT_ID)).thenReturn(Optional.of(testInstrument()));
        when(instrumentPriceService.fetchPrices(eq(USER), eq(LEDGER_SLUG), eq(INSTRUMENT_ID), any()))
                .thenThrow(new InstrumentPriceNotAvailableException("IWDA", "IE00B4L5Y983"));

        mockMvc.perform(post("/v1/ledgers/main/instrument-prices/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"instrumentId\":\"instr-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));
    }

    @Test
    void fetchPrices_instrumentWithoutIsin_returns400() throws Exception {
        when(instrumentService.find(USER, LEDGER_SLUG, INSTRUMENT_ID)).thenReturn(Optional.of(testInstrument()));
        when(instrumentPriceService.fetchPrices(eq(USER), eq(LEDGER_SLUG), eq(INSTRUMENT_ID), any()))
                .thenThrow(new IllegalArgumentException("Instrument IWDA has no ISIN; set one before fetching prices"));

        mockMvc.perform(post("/v1/ledgers/main/instrument-prices/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"instrumentId\":\"instr-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status", is("400")));
    }

    @Test
    void fetchPrices_blankInstrumentId_returns400() throws Exception {
        mockMvc.perform(post("/v1/ledgers/main/instrument-prices/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"instrumentId\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status", is("400")));
    }
}
