package com.rslima.ricash.ledgers;

import com.rslima.ricash.configuration.JsonApiPagination;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/v1/ledgers", produces = JSON_API_VALUE)
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
        var ledgerResources = ledgerService.listUserLedgers(principal.getName(), pageable)
                .map(ledger -> toEntityModel(ledger, principal));

        return JsonApiPagination.pagedModel(ledgerResources,
                p -> methodOn(LedgerController.class).listLedgers(principal, p, size));
    }

    @GetMapping("/{slug}")
    public EntityModel<LedgerResource> getLedger(@PathVariable final String slug,
                                                  JwtAuthenticationToken principal) {
        final var ledger = ledgerService.findBySlug(principal.getName(), slug)
                .orElseThrow(() -> new LedgerNotFoundException(slug));

        return toEntityModel(ledger, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<LedgerResource>> createLedger(
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateLedgerRequest request) {

        Ledger createdLedger = ledgerService.create(principal.getName(), request);
        EntityModel<LedgerResource> entityModel = toEntityModel(createdLedger, principal);

        return ResponseEntity
                .created(linkTo(methodOn(LedgerController.class).getLedger(createdLedger.slug(), principal)).toUri())
                .body(entityModel);
    }

    @PutMapping("/{slug}")
    public EntityModel<LedgerResource> updateLedger(
            @PathVariable String slug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody UpdateLedgerRequest request) {

        Ledger updatedLedger = ledgerService.update(principal.getName(), slug, request);
        return toEntityModel(updatedLedger, principal);
    }

    private EntityModel<LedgerResource> toEntityModel(Ledger ledger, JwtAuthenticationToken principal) {
        LedgerResource resource = ledgerMapper.toResource(ledger);
        EntityModel<LedgerResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(LedgerController.class).getLedger(ledger.slug(), principal)).withSelfRel());
        return entityModel;
    }

}
