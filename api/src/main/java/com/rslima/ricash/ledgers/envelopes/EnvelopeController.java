package com.rslima.ricash.ledgers.envelopes;

import com.rslima.ricash.ledgers.LedgerNotFoundException;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/ledgers/{ledgerSlug}/envelopes", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class EnvelopeController {

    private final EnvelopeService envelopeService;
    private final EnvelopeMapper envelopeMapper;

    @GetMapping
    public PagedModel<EntityModel<EnvelopeResource>> listEnvelopes(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        var envelopeResources = envelopeService.listLedgerEnvelopes(getUserId(principal), ledgerSlug, pageable)
                .map(envelope -> toEntityModel(ledgerSlug, envelope, principal));

        return buildPagedEnvelopeResponse(ledgerSlug, page, size, envelopeResources, principal);
    }

    @GetMapping("/{envelopeId}")
    public EntityModel<EnvelopeResource> getEnvelope(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            JwtAuthenticationToken principal) {

        final var envelope = envelopeService.find(getUserId(principal), ledgerSlug, envelopeId)
                .orElseThrow(() -> new EnvelopeNotFoundException(envelopeId));

        return toEntityModel(ledgerSlug, envelope, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<EnvelopeResource>> createEnvelope(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateEnvelopeRequest request) {

        Envelope createdEnvelope = envelopeService.create(getUserId(principal), ledgerSlug, request);
        EntityModel<EnvelopeResource> entityModel = toEntityModel(ledgerSlug, createdEnvelope, principal);

        return ResponseEntity
                .created(linkTo(methodOn(EnvelopeController.class).getEnvelope(ledgerSlug, createdEnvelope.id(), principal)).toUri())
                .body(entityModel);
    }

    @PutMapping("/{envelopeId}")
    public EntityModel<EnvelopeResource> updateEnvelope(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            JwtAuthenticationToken principal,
            @Valid @RequestBody UpdateEnvelopeRequest request) {

        Envelope updatedEnvelope = envelopeService.update(getUserId(principal), ledgerSlug, envelopeId, request);
        return toEntityModel(ledgerSlug, updatedEnvelope, principal);
    }

    @DeleteMapping("/{envelopeId}")
    public ResponseEntity<Void> deleteEnvelope(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            JwtAuthenticationToken principal) {

        envelopeService.delete(getUserId(principal), ledgerSlug, envelopeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{envelopeId}/allocations")
    public ResponseEntity<EntityModel<EnvelopeAllocationResource>> allocate(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            JwtAuthenticationToken principal,
            @Valid @RequestBody AllocateEnvelopeRequest request) {

        EnvelopeAllocation allocation = envelopeService.allocate(getUserId(principal), ledgerSlug, envelopeId, request);
        EnvelopeAllocationResource resource = envelopeMapper.toResource(allocation);
        EntityModel<EnvelopeAllocationResource> entityModel = EntityModel.of(resource);

        return ResponseEntity
                .created(linkTo(methodOn(EnvelopeController.class).getBalance(ledgerSlug, envelopeId, request.year(), request.month(), principal)).toUri())
                .body(entityModel);
    }

    @GetMapping("/{envelopeId}/balance")
    public EntityModel<EnvelopeBalanceResource> getBalance(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            @RequestParam int year,
            @RequestParam int month,
            JwtAuthenticationToken principal) {

        EnvelopeBalance balance = envelopeService.getBalance(getUserId(principal), ledgerSlug, envelopeId, year, month);
        EnvelopeBalanceResource resource = envelopeMapper.toResource(balance);
        return EntityModel.of(resource);
    }

    @GetMapping("/{envelopeId}/accounts")
    public ResponseEntity<Map<String, List<String>>> getEnvelopeAccounts(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            JwtAuthenticationToken principal) {

        List<String> accountIds = envelopeService.getEnvelopeAccounts(getUserId(principal), ledgerSlug, envelopeId);
        return ResponseEntity.ok(Map.of("accountIds", accountIds));
    }

    @PutMapping("/{envelopeId}/accounts")
    public ResponseEntity<Map<String, List<String>>> setEnvelopeAccounts(
            @PathVariable String ledgerSlug,
            @PathVariable String envelopeId,
            JwtAuthenticationToken principal,
            @RequestBody List<String> accountIds) {

        envelopeService.setEnvelopeAccounts(getUserId(principal), ledgerSlug, envelopeId, accountIds);
        return ResponseEntity.ok(Map.of("accountIds", accountIds));
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }

    private EntityModel<EnvelopeResource> toEntityModel(String ledgerSlug, Envelope envelope, JwtAuthenticationToken principal) {
        EnvelopeResource resource = envelopeMapper.toResource(envelope);
        EntityModel<EnvelopeResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(EnvelopeController.class).getEnvelope(ledgerSlug, envelope.id(), principal)).withSelfRel());
        return entityModel;
    }

    private PagedModel<EntityModel<EnvelopeResource>> buildPagedEnvelopeResponse(
            String ledgerSlug,
            int page,
            int size,
            Page<EntityModel<EnvelopeResource>> envelopeResources,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                envelopeResources.getContent(),
                new PagedModel.PageMetadata(
                        envelopeResources.getSize(),
                        envelopeResources.getNumber(),
                        envelopeResources.getTotalElements(),
                        envelopeResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(EnvelopeController.class).listEnvelopes(ledgerSlug, principal, page, size)).withSelfRel());
        pagedModel.add(linkTo(methodOn(EnvelopeController.class).listEnvelopes(ledgerSlug, principal, 0, size)).withRel("first"));

        if (envelopeResources.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(EnvelopeController.class).listEnvelopes(
                    ledgerSlug,
                    principal,
                    envelopeResources.getTotalPages() - 1,
                    size)).withRel("last"));
        }
        if (envelopeResources.hasNext()) {
            pagedModel.add(linkTo(methodOn(EnvelopeController.class).listEnvelopes(
                    ledgerSlug,
                    principal,
                    envelopeResources.getNumber() + 1,
                    size)).withRel("next"));
        }
        if (envelopeResources.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(EnvelopeController.class).listEnvelopes(
                    ledgerSlug,
                    principal,
                    envelopeResources.getNumber() - 1,
                    size)).withRel("prev"));
        }

        return pagedModel;
    }

    @ExceptionHandler(EnvelopeNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleEnvelopeNotFoundException(EnvelopeNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(NOT_FOUND.value()))
                                .withTitle(NOT_FOUND.getReasonPhrase())
                                .withDetail(ex.getMessage())));
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

    @ExceptionHandler(EnvelopeHasTransactionsException.class)
    public ResponseEntity<JsonApiErrors> handleEnvelopeHasTransactionsException(EnvelopeHasTransactionsException ex) {
        return ResponseEntity.status(CONFLICT).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(CONFLICT.value()))
                                .withTitle(CONFLICT.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }
}
