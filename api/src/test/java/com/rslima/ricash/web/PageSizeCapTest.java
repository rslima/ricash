package com.rslima.ricash.web;

import com.rslima.ricash.configuration.SecurityConfiguration;
import com.rslima.ricash.ledgers.accounts.AccountController;
import com.rslima.ricash.ledgers.accounts.AccountMapperImpl;
import com.rslima.ricash.ledgers.accounts.AccountService;
import com.rslima.ricash.testsupport.WebTestConfiguration;
import com.toedter.spring.hateoas.jsonapi.JsonApiMediaTypeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An oversized {@code page[size]} is clamped, and the links carry the effective size. */
@WebMvcTest(controllers = AccountController.class)
@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)
@Import({WebTestConfiguration.class, SecurityConfiguration.class, AccountMapperImpl.class})
class PageSizeCapTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void oversizedPageSize_isClampedInQueryAndLinks() throws Exception {
        when(accountService.listLedgerAccounts(any(), eq("main"),
                argThat((Pageable p) -> p.getPageSize() == Pages.MAX_PAGE_SIZE)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(2), 0));

        mockMvc.perform(get("/v1/ledgers/main/accounts")
                        .param("page[size]", "1000000")
                        .with(jwtFor("user-1")).accept(JSON_API_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page.size", is(Pages.MAX_PAGE_SIZE)))
                .andExpect(jsonPath("$.links.self",
                        is("http://localhost/v1/ledgers/main/accounts?page%5Bnumber%5D=0&page%5Bsize%5D=" + Pages.MAX_PAGE_SIZE)));
    }

    @Test
    void negativePage_isRejectedAs400() throws Exception {
        mockMvc.perform(get("/v1/ledgers/main/accounts")
                        .param("page[number]", "-1")
                        .with(jwtFor("user-1")).accept(JSON_API_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status", is("400")));
    }
}
