package com.rslima.ricash.ledgers.transactions.export;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static com.rslima.ricash.web.AuthenticatedUser.userId;

/**
 * File download of a ledger's transactions. Kept out of TransactionController
 * because this endpoint serves attachments, not JSON:API documents: no
 * {@code produces} is declared and the Content-Type is set on the response so
 * clients' JSON:API Accept headers do not fail content negotiation.
 */
@RestController
@RequestMapping("/v1/ledgers/{ledgerSlug}/transactions/export")
@RequiredArgsConstructor
public class TransactionExportController {

    private final TransactionExportService transactionExportService;

    @GetMapping
    public ResponseEntity<byte[]> exportTransactions(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam String format,
            @RequestParam(required = false) String accountId,
            @RequestParam(defaultValue = "false") boolean includeChildren,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        final var file = transactionExportService.export(userId(principal), ledgerSlug,
                new ExportRequest(format, accountId, includeChildren, from, to));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
