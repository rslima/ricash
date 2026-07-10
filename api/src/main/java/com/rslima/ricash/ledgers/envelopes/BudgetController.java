package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.DateRanges;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.rslima.ricash.web.AuthenticatedUser.userId;

@RestController
@RequestMapping(value = "/v1/ledgers/{ledgerSlug}")
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

        var balances = envelopeService.getBudgetSummary(userId(principal), ledgerSlug, year, month);
        var toBeBudgeted = envelopeService.getToBeBudgeted(userId(principal), ledgerSlug, year, month);

        var resource = new BudgetSummaryResource(
                DateRanges.periodId(year, month),
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

        Map<String, String> mappings = envelopeService.getAllEnvelopeMappings(userId(principal), ledgerSlug);
        return ResponseEntity.ok(mappings);
    }
}
