package com.rslima.ricash.web;

import com.rslima.ricash.configuration.SecurityConfiguration;
import com.rslima.ricash.ledgers.LedgerNotFoundException;
import com.rslima.ricash.ledgers.accounts.AccountController;
import com.rslima.ricash.ledgers.accounts.AccountHasTransactionsException;
import com.rslima.ricash.ledgers.accounts.AccountMapper;
import com.rslima.ricash.ledgers.accounts.AccountService;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateController;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateMapper;
import com.rslima.ricash.ledgers.exchangerates.ExchangeRateService;
import com.rslima.ricash.ledgers.transactions.TransactionController;
import com.rslima.ricash.ledgers.transactions.TransactionMapper;
import com.rslima.ricash.ledgers.transactions.TransactionService;
import com.rslima.ricash.testsupport.WebTestConfiguration;
import com.toedter.spring.hateoas.jsonapi.JsonApiMediaTypeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the unified error contract: every application error renders as a
 * JSON:API error document with one status mapping regardless of which
 * controller it passes through (previously each controller had its own
 * copy-pasted handlers with diverging statuses: 404/400/422/500).
 */
@WebMvcTest(controllers = {AccountController.class, TransactionController.class, ExchangeRateController.class})
@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)
@Import({WebTestConfiguration.class, SecurityConfiguration.class})
class GlobalExceptionHandlerTest {

    private static final String USER = "user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;
    @MockitoBean
    private AccountMapper accountMapper;
    @MockitoBean
    private TransactionService transactionService;
    @MockitoBean
    private TransactionMapper transactionMapper;
    @MockitoBean
    private ExchangeRateService exchangeRateService;
    @MockitoBean
    private ExchangeRateMapper exchangeRateMapper;

    @Test
    void unknownLedger_onAccounts_returns404JsonApiError() throws Exception {
        when(accountService.listLedgerAccounts(any(), any(), any()))
                .thenThrow(new LedgerNotFoundException("ghost"));

        mockMvc.perform(get("/v1/ledgers/ghost/accounts").with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")))
                .andExpect(jsonPath("$.errors[0].title", is("Not Found")))
                .andExpect(jsonPath("$.errors[0].detail", is("Ledger not found: ghost")));
    }

    @Test
    void unknownLedger_onTransactions_returns404InsteadOf500() throws Exception {
        // TransactionController had no LedgerNotFoundException handler before:
        // this used to escape as a 500.
        when(transactionService.listLedgerTransactions(any(), any(), any()))
                .thenThrow(new LedgerNotFoundException("ghost"));

        mockMvc.perform(get("/v1/ledgers/ghost/transactions").with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));
    }

    @Test
    void accountWithTransactions_onDelete_returns409JsonApiError() throws Exception {
        doThrow(new AccountHasTransactionsException("acct-1"))
                .when(accountService).delete(any(), any(), any());

        mockMvc.perform(delete("/v1/ledgers/main/accounts/acct-1").with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].status", is("409")))
                .andExpect(jsonPath("$.errors[0].detail", containsString("acct-1")));
    }

    @Test
    void validationFailure_returnsJsonApiErrorsPerField() throws Exception {
        // name, currency and type are all missing -> three field errors,
        // rendered as JSON:API errors instead of the former RFC-7807 body.
        mockMvc.perform(post("/v1/ledgers/main/accounts")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].status", is("400")));
    }

    @Test
    void illegalArgument_onExchangeRates_returns400InsteadOf422() throws Exception {
        when(exchangeRateService.refreshRate(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Cannot fetch exchange rate for same currency: USD"));

        mockMvc.perform(post("/v1/exchange-rates/fetch")
                        .with(jwtFor(USER))
                        .contentType("application/json")
                        .accept(JSON_API_VALUE)
                        .content("{\"fromCurrency\":\"USD\",\"toCurrency\":\"USD\",\"date\":\"2026-01-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status", is("400")))
                .andExpect(jsonPath("$.errors[0].detail", containsString("same currency")));
    }

    @Test
    void unexpectedException_returns500WithGenericJsonApiError() throws Exception {
        when(accountService.listLedgerAccounts(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/v1/ledgers/main/accounts").with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errors[0].status", is("500")))
                .andExpect(jsonPath("$.errors[0].detail", is("An unexpected error occurred")));
    }
}
