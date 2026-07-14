package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.configuration.SecurityConfiguration;
import com.rslima.ricash.ledgers.accounts.AccountNotFoundException;
import com.rslima.ricash.testsupport.WebTestConfiguration;
import com.toedter.spring.hateoas.jsonapi.JsonApiMediaTypeConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.rslima.ricash.testsupport.WebTestConfiguration.jwtFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionExportController.class)
@ImportAutoConfiguration(JsonApiMediaTypeConfiguration.class)
@Import({WebTestConfiguration.class, SecurityConfiguration.class})
class TransactionExportControllerTest {

    private static final String USER = "user-1";
    private static final String EXPORT_URL = "/v1/ledgers/main/transactions/export";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionExportService transactionExportService;

    @Test
    void export_csv_returnsAttachmentWithCsvContentType() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenReturn(new ExportFile("main-transactions-all.csv", "text/csv; charset=UTF-8",
                        "header\r\nrow".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(EXPORT_URL).param("format", "csv").with(jwtFor(USER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"main-transactions-all.csv\""))
                .andExpect(content().string("header\r\nrow"));
    }

    @Test
    void export_ofx_returnsAttachmentWithOfxContentType() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenReturn(new ExportFile("main-transactions-all.ofx", "application/x-ofx",
                        "OFXHEADER:100".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(EXPORT_URL).param("format", "ofx").with(jwtFor(USER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/x-ofx"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"main-transactions-all.ofx\""))
                .andExpect(content().string("OFXHEADER:100"));
    }

    @Test
    void export_forwardsAllParametersToService() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenReturn(new ExportFile("f.csv", "text/csv; charset=UTF-8", new byte[0]));

        mockMvc.perform(get(EXPORT_URL)
                        .param("format", "csv")
                        .param("accountId", "acc-1")
                        .param("includeChildren", "true")
                        .param("from", "2026-01-01")
                        .param("to", "2026-06-30")
                        .with(jwtFor(USER)))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ExportRequest.class);
        verify(transactionExportService).export(eq(USER), eq("main"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ExportRequest(
                "csv", "acc-1", true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)));
    }

    @Test
    void export_defaultsOptionalParameters() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenReturn(new ExportFile("f.csv", "text/csv; charset=UTF-8", new byte[0]));

        mockMvc.perform(get(EXPORT_URL).param("format", "csv").with(jwtFor(USER)))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(ExportRequest.class);
        verify(transactionExportService).export(eq(USER), eq("main"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ExportRequest("csv", null, false, null, null));
    }

    @Test
    void export_withoutFormat_returns400() throws Exception {
        mockMvc.perform(get(EXPORT_URL).with(jwtFor(USER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void export_invalidFormat_returns400ErrorDocument() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenThrow(new IllegalArgumentException("Unsupported export format: xml"));

        mockMvc.perform(get(EXPORT_URL).param("format", "xml").with(jwtFor(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].status", is("400")));
    }

    @Test
    void export_unknownAccount_returns404ErrorDocument() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenThrow(new AccountNotFoundException("ghost"));

        mockMvc.perform(get(EXPORT_URL).param("format", "csv").param("accountId", "ghost").with(jwtFor(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].status", is("404")));
    }

    @Test
    void export_crossOrigin_exposesContentDispositionHeader() throws Exception {
        when(transactionExportService.export(eq(USER), eq("main"), any()))
                .thenReturn(new ExportFile("f.csv", "text/csv; charset=UTF-8", new byte[0]));

        mockMvc.perform(get(EXPORT_URL).param("format", "csv")
                        .header("Origin", "http://localhost:5173")
                        .with(jwtFor(USER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"));
    }

    @Test
    void export_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get(EXPORT_URL).param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }
}
