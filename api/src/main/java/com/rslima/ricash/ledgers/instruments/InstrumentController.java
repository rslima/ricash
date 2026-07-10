package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.web.PagedModels;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.hateoas.CollectionModel;

import static com.rslima.ricash.web.AuthenticatedUser.userId;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(value = "/v1/ledgers/{ledgerSlug}/instruments", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final InstrumentMapper instrumentMapper;

    @GetMapping
    public PagedModel<EntityModel<InstrumentResource>> listInstruments(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        Page<EntityModel<InstrumentResource>> instrumentResources = instrumentService
                .listByLedger(userId(principal), ledgerSlug, pageable)
                .map(instrument -> toEntityModel(instrument, ledgerSlug, principal));

        return PagedModels.toPagedModel(instrumentResources,
                p -> methodOn(InstrumentController.class).listInstruments(ledgerSlug, principal, p, size));
    }

    @GetMapping("/all")
    public CollectionModel<EntityModel<InstrumentResource>> listAllInstruments(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {

        List<EntityModel<InstrumentResource>> resources = instrumentService
                .listAllByLedger(userId(principal), ledgerSlug).stream()
                .map(instrument -> toEntityModel(instrument, ledgerSlug, principal))
                .toList();
        return CollectionModel.of(resources);
    }

    @GetMapping("/{instrumentId}")
    public EntityModel<InstrumentResource> getInstrument(
            @PathVariable String ledgerSlug,
            @PathVariable String instrumentId,
            JwtAuthenticationToken principal) {

        final var instrument = instrumentService.find(userId(principal), ledgerSlug, instrumentId)
                .orElseThrow(() -> new InstrumentNotFoundException(instrumentId));

        return toEntityModel(instrument, ledgerSlug, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<InstrumentResource>> createInstrument(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateInstrumentRequest request) {

        Instrument created = instrumentService.create(
                userId(principal),
                ledgerSlug,
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
                userId(principal),
                ledgerSlug,
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

        instrumentService.delete(userId(principal), ledgerSlug, instrumentId);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<InstrumentResource> toEntityModel(Instrument instrument, String ledgerSlug, JwtAuthenticationToken principal) {
        InstrumentResource resource = instrumentMapper.toResource(instrument);
        EntityModel<InstrumentResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(InstrumentController.class)
                .getInstrument(ledgerSlug, instrument.id(), principal)).withSelfRel());
        return entityModel;
    }
}
