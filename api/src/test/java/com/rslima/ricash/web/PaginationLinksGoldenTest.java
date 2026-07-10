package com.rslima.ricash.web;

import com.rslima.ricash.configuration.SecurityConfiguration;
import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountController;
import com.rslima.ricash.ledgers.accounts.AccountMapperImpl;
import com.rslima.ricash.ledgers.accounts.AccountService;
import com.rslima.ricash.ledgers.accounts.AccountStatus;
import com.rslima.ricash.ledgers.accounts.AccountType;
import com.rslima.ricash.ledgers.transactions.Transaction;
import com.rslima.ricash.ledgers.transactions.TransactionController;
import com.rslima.ricash.ledgers.transactions.TransactionMapperImpl;
import com.rslima.ricash.ledgers.transactions.TransactionService;
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

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Golden test for the JSON:API pagination links and page metadata: the exact
 * hrefs must stay byte-identical when the per-controller link-building
 * boilerplate is replaced by the shared PagedModels helper.
 */
@WebMvcTest(controllers = {AccountController.class, TransactionController.class})
@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)
@Import({WebTestConfiguration.class, SecurityConfiguration.class, AccountMapperImpl.class, TransactionMapperImpl.class})
class PaginationLinksGoldenTest {

    private static final String USER = "user-1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;
    @MockitoBean
    private TransactionService transactionService;

    @Test
    void accountList_middlePage_hasAllNavigationLinks() throws Exception {
        var account = new Account("acct-1", "checking", "Checking", null, "USD",
                AccountType.ASSET, AccountStatus.ACTIVE, BigDecimal.TEN, Instant.now(), null, List.of());
        var page = new PageImpl<>(List.of(account), PageRequest.of(1, 2), 6);
        when(accountService.listLedgerAccounts(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/v1/ledgers/main/accounts")
                        .param("page[number]", "1")
                        .param("page[size]", "2")
                        .with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page.size", is(2)))
                .andExpect(jsonPath("$.meta.page.number", is(1)))
                .andExpect(jsonPath("$.meta.page.totalElements", is(6)))
                .andExpect(jsonPath("$.meta.page.totalPages", is(3)))
                .andExpect(jsonPath("$.links.self", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=1&page%5Bsize%5D=2")))
                .andExpect(jsonPath("$.links.first", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=0&page%5Bsize%5D=2")))
                .andExpect(jsonPath("$.links.last", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=2&page%5Bsize%5D=2")))
                .andExpect(jsonPath("$.links.next", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=2&page%5Bsize%5D=2")))
                .andExpect(jsonPath("$.links.prev", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=0&page%5Bsize%5D=2")));
    }

    @Test
    void accountList_firstAndOnlyPage_hasSelfFirstLastOnly() throws Exception {
        var account = new Account("acct-1", "checking", "Checking", null, "USD",
                AccountType.ASSET, AccountStatus.ACTIVE, BigDecimal.TEN, Instant.now(), null, List.of());
        var page = new PageImpl<>(List.of(account), PageRequest.of(0, 20), 1);
        when(accountService.listLedgerAccounts(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/v1/ledgers/main/accounts")
                        .with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links.self", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=0&page%5Bsize%5D=20")))
                .andExpect(jsonPath("$.links.first", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=0&page%5Bsize%5D=20")))
                .andExpect(jsonPath("$.links.last", is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=0&page%5Bsize%5D=20")))
                .andExpect(jsonPath("$.links.next").doesNotExist())
                .andExpect(jsonPath("$.links.prev").doesNotExist());
    }

    @Test
    void transactionList_linksCarryNullFiltersAsBefore() throws Exception {
        var transaction = new Transaction("tx-1", LocalDate.of(2026, 1, 10), Instant.now(), "Salary", List.of(), List.of());
        var page = new PageImpl<>(List.of(transaction), PageRequest.of(0, 20), 25);
        when(transactionService.listLedgerTransactions(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/v1/ledgers/main/transactions")
                        .with(jwtFor(USER)).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page.totalElements", is(25)))
                // The null filter params make these links URI templates - pinned as-is.
                .andExpect(jsonPath("$.links.self.href",
                        is("http://localhost/v1/ledgers/main/transactions?page%5Bnumber%5D=0&page%5Bsize%5D=20%7B&accountId,description,year,month%7D")))
                .andExpect(jsonPath("$.links.self.meta.isTemplated", is(true)))
                .andExpect(jsonPath("$.links.next.href",
                        is("http://localhost/v1/ledgers/main/transactions?page%5Bnumber%5D=1&page%5Bsize%5D=20%7B&accountId,description,year,month%7D")))
                .andExpect(jsonPath("$.links.last.href",
                        is("http://localhost/v1/ledgers/main/transactions?page%5Bnumber%5D=1&page%5Bsize%5D=20%7B&accountId,description,year,month%7D")));
    }
}
