package com.rslima.ricash.ledgers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rslima.ricash.TestRicashApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private LedgerService ledgerService;

    @Autowired
    private LedgerMapper ledgerMapper;

    private static final String USER_ID = "test-user";
    private static final String LEDGER_ID = "01234567-89ab-cdef-0123-456789abcdef";
    private static final String LEDGER_SLUG = "test-ledger";

    @Test
    void listLedgers_returnsPagedLedgers() throws Exception {
        var ledger = createTestLedger();
        var page = new PageImpl<>(List.of(ledger), PageRequest.of(0, 20), 1);

        when(ledgerService.listUserLedgers(any(), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/ledgers")
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id", is(LEDGER_ID)))
                .andExpect(jsonPath("$.data[0].type", is("ledgers")))
                .andExpect(jsonPath("$.data[0].attributes.name", is("Test Ledger")));
    }

    @Test
    void listLedgers_withPagination_returnsCorrectPage() throws Exception {
        var ledger = createTestLedger();
        var page = new PageImpl<>(List.of(ledger), PageRequest.of(1, 10), 25);

        when(ledgerService.listUserLedgers(any(), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/ledgers")
                        .param("page[number]", "1")
                        .param("page[size]", "10")
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void listLedgers_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ledgers")
                        .accept(JSON_API_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getLedger_returnsLedger() throws Exception {
        var ledger = createTestLedger();

        when(ledgerService.findBySlug(any(), eq(LEDGER_SLUG))).thenReturn(Optional.of(ledger));

        mockMvc.perform(get("/api/v1/ledgers/{slug}", LEDGER_SLUG)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(LEDGER_ID)))
                .andExpect(jsonPath("$.data.type", is("ledgers")))
                .andExpect(jsonPath("$.data.attributes.name", is("Test Ledger")))
                .andExpect(jsonPath("$.data.attributes.currency", is("USD")));
    }

    @Test
    void getLedger_notFound_returns404() throws Exception {
        when(ledgerService.findBySlug(any(), eq(LEDGER_SLUG))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/ledgers/{slug}", LEDGER_SLUG)
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")))
                .andExpect(jsonPath("$.errors[0].title", is("Not Found")));
    }

    @Test
    void getLedger_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ledgers/{slug}", LEDGER_SLUG)
                        .accept(JSON_API_VALUE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createLedger_returnsCreatedLedger() throws Exception {
        var request = new CreateLedgerRequest("New Ledger", "Description", "EUR");
        var ledger = new Ledger(LEDGER_ID, USER_ID, "new-ledger", "New Ledger", "Description", "EUR", Instant.now(), List.of(), List.of());

        when(ledgerService.create(any(), any(CreateLedgerRequest.class))).thenReturn(ledger);

        mockMvc.perform(post("/api/v1/ledgers")
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/ledgers/new-ledger")))
                .andExpect(jsonPath("$.data.id", is(LEDGER_ID)))
                .andExpect(jsonPath("$.data.type", is("ledgers")))
                .andExpect(jsonPath("$.data.attributes.name", is("New Ledger")));
    }

    @Test
    void createLedger_withoutName_returnsBadRequest() throws Exception {
        var request = new CreateLedgerRequest(null, "Description", "EUR");

        mockMvc.perform(post("/api/v1/ledgers")
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLedger_withoutCurrency_returnsBadRequest() throws Exception {
        var request = new CreateLedgerRequest("Name", "Description", null);

        mockMvc.perform(post("/api/v1/ledgers")
                        .with(jwt().jwt(builder -> builder.claim("preferred_username", USER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createLedger_withoutAuthentication_returnsForbidden() throws Exception {
        var request = new CreateLedgerRequest("New Ledger", "Description", "EUR");

        mockMvc.perform(post("/api/v1/ledgers")
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private Ledger createTestLedger() {
        return new Ledger(LEDGER_ID, USER_ID, LEDGER_SLUG, "Test Ledger", "Test Description", "USD", Instant.now(), List.of(), List.of());
    }
}
