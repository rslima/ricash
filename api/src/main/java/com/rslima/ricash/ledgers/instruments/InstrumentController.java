package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.LedgerService;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.hateoas.CollectionModel;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/ledgers/{ledgerSlug}/instruments", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentMapper instrumentMapper;
    private final LedgerService ledgerService;

    public record CreateInstrumentRequest(
            @NotBlank String symbol,
            @NotBlank String name,
            @NotNull InstrumentType type,
            @NotBlank String currency,
            String market,
            String isin
    ) {}

    public record UpdateInstrumentRequest(
            @NotBlank String symbol,
            @NotBlank String name,
            @NotNull InstrumentType type,
            @NotBlank String currency,
            String market,
            String isin,
            @NotNull InstrumentStatus status
    ) {}

    @GetMapping
    public PagedModel<EntityModel<InstrumentResource>> listInstruments(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        String ledgerId = getLedgerId(principal, ledgerSlug);
        final var pageable = PageRequest.of(page, size);
        Page<EntityModel<InstrumentResource>> instrumentResources = instrumentService.listByLedger(ledgerId, pageable)
                .map(instrument -> toEntityModel(instrument, ledgerSlug, principal));

        return buildPagedResponse(ledgerSlug, page, size, instrumentResources, principal);
    }

    @GetMapping("/all")
    public CollectionModel<EntityModel<InstrumentResource>> listAllInstruments(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {

        String ledgerId = getLedgerId(principal, ledgerSlug);
        List<EntityModel<InstrumentResource>> resources = instrumentService.listAllByLedger(ledgerId).stream()
                .map(instrument -> toEntityModel(instrument, ledgerSlug, principal))
                .toList();
        return CollectionModel.of(resources);
    }

    @GetMapping("/{instrumentId}")
    public EntityModel<InstrumentResource> getInstrument(
            @PathVariable String ledgerSlug,
            @PathVariable String instrumentId,
            JwtAuthenticationToken principal) {

        final var instrument = instrumentService.findById(instrumentId)
                .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));

        return toEntityModel(instrument, ledgerSlug, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<InstrumentResource>> createInstrument(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateInstrumentRequest request) {

        String ledgerId = getLedgerId(principal, ledgerSlug);
        Instrument created = instrumentService.create(
                ledgerId,
                request.symbol(),
                request.name(),
                request.type(),
                request.currency(),
                request.market(),
                request.isin()
        );

        EntityModel<InstrumentResource> entityModel = toEntityModel(created, ledgerSlug, principal);

        return ResponseEntity
                .created(linkTo(methodOn(InstrumentController.class)
                        .getInstrument(ledgerSlug, created.id(), principal)).toUri())
                .body(entityModel);
    }

    @PutMapping("/{instrumentId}")
    public EntityModel<InstrumentResource> updateInstrument(
            @PathVariable String ledgerSlug,
            @PathVariable String instrumentId,
            JwtAuthenticationToken principal,
            @Valid @RequestBody UpdateInstrumentRequest request) {

        Instrument updated = instrumentService.update(
                instrumentId,
                request.symbol(),
                request.name(),
                request.type(),
                request.currency(),
                request.market(),
                request.isin(),
                request.status()
        );

        return toEntityModel(updated, ledgerSlug, principal);
    }

    @DeleteMapping("/{instrumentId}")
    public ResponseEntity<Void> deleteInstrument(
            @PathVariable String ledgerSlug,
            @PathVariable String instrumentId,
            JwtAuthenticationToken principal) {

        instrumentService.delete(instrumentId);
        return ResponseEntity.noContent().build();
    }

    private String getLedgerId(JwtAuthenticationToken principal, String ledgerSlug) {
        return ledgerService.findBySlug(getUserId(principal), ledgerSlug)
                .orElseThrow(() -> new IllegalArgumentException("Ledger not found: " + ledgerSlug))
                .id();
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }

    private EntityModel<InstrumentResource> toEntityModel(Instrument instrument, String ledgerSlug, JwtAuthenticationToken principal) {
        InstrumentResource resource = instrumentMapper.toResource(instrument);
        EntityModel<InstrumentResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(InstrumentController.class)
                .getInstrument(ledgerSlug, instrument.id(), principal)).withSelfRel());
        return entityModel;
    }

    private PagedModel<EntityModel<InstrumentResource>> buildPagedResponse(
            String ledgerSlug,
            int page,
            int size,
            Page<EntityModel<InstrumentResource>> instrumentResources,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                instrumentResources.getContent(),
                new PagedModel.PageMetadata(
                        instrumentResources.getSize(),
                        instrumentResources.getNumber(),
                        instrumentResources.getTotalElements(),
                        instrumentResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(InstrumentController.class)
                .listInstruments(ledgerSlug, principal, page, size)).withSelfRel());
        pagedModel.add(linkTo(methodOn(InstrumentController.class)
                .listInstruments(ledgerSlug, principal, 0, size)).withRel("first"));

        if (instrumentResources.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(InstrumentController.class).listInstruments(
                    ledgerSlug,
                    principal,
                    instrumentResources.getTotalPages() - 1,
                    size)).withRel("last"));
        }
        if (instrumentResources.hasNext()) {
            pagedModel.add(linkTo(methodOn(InstrumentController.class).listInstruments(
                    ledgerSlug,
                    principal,
                    instrumentResources.getNumber() + 1,
                    size)).withRel("next"));
        }
        if (instrumentResources.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(InstrumentController.class).listInstruments(
                    ledgerSlug,
                    principal,
                    instrumentResources.getNumber() - 1,
                    size)).withRel("prev"));
        }

        return pagedModel;
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleInstrumentNotFoundException(InstrumentNotFoundException ex) {
        return ResponseEntity.status(NOT_FOUND).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(NOT_FOUND.value()))
                                .withTitle(NOT_FOUND.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<JsonApiErrors> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(BAD_REQUEST).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(BAD_REQUEST.value()))
                                .withTitle(BAD_REQUEST.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }

    public static class InstrumentNotFoundException extends RuntimeException {
        public InstrumentNotFoundException(String id) {
            super("Instrument not found: " + id);
        }
    }
}
