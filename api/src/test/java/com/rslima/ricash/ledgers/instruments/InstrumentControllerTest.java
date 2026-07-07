package com.rslima.ricash.ledgers.instruments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
class InstrumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private InstrumentService instrumentService;

    @MockitoBean
    private LedgerService ledgerService;

    private static final String OWNER_ID = "owner-user";
    private static final String ATTACKER_ID = "attacker-user";
    private static final String LEDGER_ID = "ledger-1";
    private static final String LEDGER_SLUG = "owner-ledger";
    private static final String INSTRUMENT_ID = "instrument-1";

    @Test
    void getInstrument_asOwner_returnsInstrument() throws Exception {
        stubLedgerForOwner();
        when(instrumentService.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.of(createTestInstrument()));

        mockMvc.perform(get("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", OWNER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(INSTRUMENT_ID)))
                .andExpect(jsonPath("$.data.attributes.symbol", is("PETR4")));
    }

    @Test
    void getInstrument_asNonOwner_returns404WithoutTouchingInstrument() throws Exception {
        stubLedgerForOwner();

        mockMvc.perform(get("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", ATTACKER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));

        verify(instrumentService, never()).findById(any(), any());
    }

    @Test
    void getInstrument_instrumentFromAnotherLedger_returns404() throws Exception {
        stubLedgerForOwner();
        when(instrumentService.findById(LEDGER_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", OWNER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));
    }

    @Test
    void updateInstrument_asNonOwner_returns404WithoutUpdating() throws Exception {
        stubLedgerForOwner();
        var request = new InstrumentController.UpdateInstrumentRequest(
                "PETR4", "Petrobras", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE);

        mockMvc.perform(put("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", ATTACKER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(instrumentService, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateInstrument_asOwner_passesLedgerScopeToService() throws Exception {
        stubLedgerForOwner();
        when(instrumentService.update(eq(LEDGER_ID), eq(INSTRUMENT_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(createTestInstrument());
        var request = new InstrumentController.UpdateInstrumentRequest(
                "PETR4", "Petrobras", InstrumentType.STOCK, "BRL", "B3", null, InstrumentStatus.ACTIVE);

        mockMvc.perform(put("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", OWNER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(INSTRUMENT_ID)));

        verify(instrumentService).update(eq(LEDGER_ID), eq(INSTRUMENT_ID), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteInstrument_asNonOwner_returns404WithoutDeleting() throws Exception {
        stubLedgerForOwner();

        mockMvc.perform(delete("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", ATTACKER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isNotFound());

        verify(instrumentService, never()).delete(any(), any());
    }

    @Test
    void deleteInstrument_asOwner_returnsNoContent() throws Exception {
        stubLedgerForOwner();

        mockMvc.perform(delete("/v1/ledgers/{slug}/instruments/{id}", LEDGER_SLUG, INSTRUMENT_ID)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", OWNER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isNoContent());

        verify(instrumentService).delete(LEDGER_ID, INSTRUMENT_ID);
    }

    private void stubLedgerForOwner() {
        var ledger = new Ledger(LEDGER_ID, OWNER_ID, LEDGER_SLUG, "Owner Ledger", null, "BRL",
                Instant.now(), List.of(), List.of());
        when(ledgerService.findBySlug(eq(OWNER_ID), eq(LEDGER_SLUG))).thenReturn(Optional.of(ledger));
        when(ledgerService.findBySlug(eq(ATTACKER_ID), eq(LEDGER_SLUG))).thenReturn(Optional.empty());
    }

    private Instrument createTestInstrument() {
        return new Instrument(INSTRUMENT_ID, LEDGER_ID, "PETR4", "Petrobras PN", InstrumentType.STOCK,
                "BRL", "B3", null, InstrumentStatus.ACTIVE, Instant.now());
    }
}
