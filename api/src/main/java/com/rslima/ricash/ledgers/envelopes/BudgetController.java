package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.LedgerNotFoundException;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/ledgers/{ledgerSlug}")
@RequiredArgsConstructor
@Slf4j
public class BudgetController {

    private final EnvelopeService envelopeService;
    private final EnvelopeMapper envelopeMapper;

    @GetMapping(value = "/budget", produces = { MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json" })
    public ResponseEntity<BudgetSummaryResource> getBudgetSummary(
            @PathVariable String ledgerSlug,
            @RequestParam int year,
            @RequestParam int month,
            JwtAuthenticationToken principal) {

        var balances = envelopeService.getBudgetSummary(getUserId(principal), ledgerSlug, year, month);
        var toBeBudgeted = envelopeService.getToBeBudgeted(getUserId(principal), ledgerSlug, year, month);

        var resource = new BudgetSummaryResource(
                String.format("%d-%02d", year, month),
                year,
                month,
                toBeBudgeted,
                envelopeMapper.toEnvelopeBalanceResources(balances)
        );

        return ResponseEntity.ok(resource);
    }

    @GetMapping(value = "/envelope-mappings", produces = { MediaType.APPLICATION_JSON_VALUE, "application/vnd.api+json" })
    public ResponseEntity<Map<String, String>> getAllEnvelopeMappings(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {

        Map<String, String> mappings = envelopeService.getAllEnvelopeMappings(getUserId(principal), ledgerSlug);
        return ResponseEntity.ok(mappings);
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }

    @ExceptionHandler(LedgerNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleLedgerNotFoundException(LedgerNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(NOT_FOUND.value()))
                                .withTitle(NOT_FOUND.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }
}
