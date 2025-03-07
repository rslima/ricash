package com.rslima.ricash.ledgers;


import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<List<Ledger>> listLedgers() {
        return ResponseEntity.ok(ledgerService.list());
    }

    @GetMapping("/:ledger")
    public ResponseEntity<Ledger> getLedger(final String ledger) {
        final var found = ledgerService.find(ledger);

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
