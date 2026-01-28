package com.rslima.ricash.ledgers;


import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/ledgers", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class LedgerController {

    private final LedgerService ledgerService;
    private final LedgerMapper ledgerMapper;

    @GetMapping
    public PagedModel<EntityModel<LedgerResource>> listLedgers(
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        var ledgerResources = ledgerService.listUserLedgers(getUserId(principal), pageable)
                .map(ledger -> toEntityModel(ledger, principal));

        return buildPagedLedgerResponse(page, size, ledgerResources, principal);
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }

    @GetMapping("/{ledgerId}")
    public EntityModel<LedgerResource> getLedger(@PathVariable final String ledgerId,
                                                  JwtAuthenticationToken principal) {
        final var ledger = ledgerService.find(getUserId(principal), ledgerId)
                .orElseThrow(() -> new LedgerNotFoundException(ledgerId));

        return toEntityModel(ledger, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<LedgerResource>> createLedger(
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateLedgerRequest request) {

        Ledger createdLedger = ledgerService.create(getUserId(principal), request);
        EntityModel<LedgerResource> entityModel = toEntityModel(createdLedger, principal);

        return ResponseEntity
                .created(linkTo(methodOn(LedgerController.class).getLedger(createdLedger.id(), principal)).toUri())
                .body(entityModel);
    }

    private EntityModel<LedgerResource> toEntityModel(Ledger ledger, JwtAuthenticationToken principal) {
        LedgerResource resource = ledgerMapper.toResource(ledger);
        EntityModel<LedgerResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(LedgerController.class).getLedger(ledger.id(), principal)).withSelfRel());
        return entityModel;
    }

    private PagedModel<EntityModel<LedgerResource>> buildPagedLedgerResponse(
            int page,
            int size,
            Page<EntityModel<LedgerResource>> ledgerResources,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                ledgerResources.getContent(),
                new PagedModel.PageMetadata(
                        ledgerResources.getSize(),
                        ledgerResources.getNumber(),
                        ledgerResources.getTotalElements(),
                        ledgerResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(LedgerController.class).listLedgers(principal, page, size)).withSelfRel());
        pagedModel.add(linkTo(methodOn(LedgerController.class).listLedgers(principal, 0, size)).withRel("first"));

        if (ledgerResources.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(LedgerController.class).listLedgers(
                    principal,
                    ledgerResources.getTotalPages() - 1,
                    size)).withRel("last"));
        }
        if (ledgerResources.hasNext()) {
            pagedModel.add(linkTo(methodOn(LedgerController.class).listLedgers(
                    principal,
                    ledgerResources.getNumber() + 1,
                    size)).withRel("next"));
        }
        if (ledgerResources.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(LedgerController.class).listLedgers(
                    principal,
                    ledgerResources.getNumber() - 1,
                    size)).withRel("prev"));
        }

        return pagedModel;
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
