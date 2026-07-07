package com.rslima.ricash.configuration;

import com.rslima.ricash.TestRicashApplication;
import com.rslima.ricash.ledgers.LedgerService;
import com.rslima.ricash.ledgers.accounts.AccountHasTransactionsException;
import com.rslima.ricash.ledgers.accounts.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestRicashApplication.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private AccountService accountService;

    private static final String USER_ID = "test-user";

    @Test
    void entityNotFound_rendersJsonApi404() throws Exception {
        when(ledgerService.findBySlug(any(), any())).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/v1/ledgers/{slug}", "missing")
                        .with(jwt().jwt(b -> b.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")))
                .andExpect(jsonPath("$.errors[0].title", is("Not Found")))
                .andExpect(jsonPath("$.errors[0].detail", containsString("missing")));
    }

    @Test
    void hasTransactionsConflict_renders409() throws Exception {
        doThrow(new AccountHasTransactionsException("account-1"))
                .when(accountService).delete(any(), any(), any());

        mockMvc.perform(delete("/v1/ledgers/{slug}/accounts/{id}", "my-ledger", "account-1")
                        .with(jwt().jwt(b -> b.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].status", is("409")));
    }

    @Test
    void validationFailure_rendersFieldPointers() throws Exception {
        mockMvc.perform(post("/v1/ledgers")
                        .with(jwt().jwt(b -> b.claim("preferred_username", USER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"description\":\"no name or currency\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status", is("400")))
                .andExpect(jsonPath("$.errors[*].source.pointer",
                        org.hamcrest.Matchers.hasItem(containsString("/data/attributes/"))));
    }

    @Test
    void malformedBody_renders400() throws Exception {
        mockMvc.perform(post("/v1/ledgers")
                        .with(jwt().jwt(b -> b.claim("preferred_username", USER_ID)))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", is("Malformed request body")));
    }

    @Test
    void unexpectedException_renders500WithoutLeakingMessage() throws Exception {
        when(ledgerService.findBySlug(any(), any()))
                .thenThrow(new IllegalStateException("secret internal state: db password"));

        mockMvc.perform(get("/v1/ledgers/{slug}", "boom")
                        .with(jwt().jwt(b -> b.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].status", is("500")))
                .andExpect(jsonPath("$.errors[0].detail", is("An unexpected error occurred")))
                .andExpect(jsonPath("$.errors[0].detail", not(containsString("secret"))));
    }

    @Test
    void illegalArgument_renders400WithDetail() throws Exception {
        when(ledgerService.findBySlug(any(), any()))
                .thenThrow(new IllegalArgumentException("Currency mismatch"));

        mockMvc.perform(get("/v1/ledgers/{slug}", "bad")
                        .with(jwt().jwt(b -> b.claim("preferred_username", USER_ID)))
                        .accept(JSON_API_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].detail", is("Currency mismatch")));
    }
}
