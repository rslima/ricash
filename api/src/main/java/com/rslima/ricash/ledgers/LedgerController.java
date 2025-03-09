package com.rslima.ricash.ledgers;


import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/ledgers")
@RequiredArgsConstructor
@Slf4j
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping
    public ResponseEntity<List<Ledger>> listLedgers(OAuth2AuthenticationToken principal) {
        return ResponseEntity.ok(ledgerService.list(getUserId(principal)));
    }

    private static @Nullable String getUserId(OAuth2AuthenticationToken principal) {
        return principal.getPrincipal().getAttribute("preferred_username");
    }

    @GetMapping("/{ledger}")
    public ResponseEntity<Ledger> getLedger(@PathVariable final String ledger,
                                            OAuth2AuthenticationToken principal) {
        final var found = ledgerService.find(getUserId(principal), ledger);

        return ResponseEntity.ok(found.orElseThrow(() -> new LedgerNotFoundException(ledger)));
    }

    @ExceptionHandler(LedgerNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleLedgerNotFoundException(LedgerNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(String.valueOf(NOT_FOUND.value()))
                                .withTitle(NOT_FOUND.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }
}
