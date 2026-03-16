package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.LedgerService;

import com.toedter.spring.hateoas.jsonapi.JsonApiError;
import com.toedter.spring.hateoas.jsonapi.JsonApiErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/ledgers/{ledgerSlug}/instrument-prices", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceController {

    private final InstrumentPriceService instrumentPriceService;
    private final InstrumentService instrumentService;
    private final InstrumentPriceMapper instrumentPriceMapper;
    private final LedgerService ledgerService;

    public record CreateInstrumentPriceRequest(
            @NotBlank String instrumentId,
            @NotNull @Positive BigDecimal price,
            @NotNull LocalDate effectiveDate
    ) {}

    @GetMapping
    public PagedModel<EntityModel<InstrumentPriceResource>> listPrices(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "50") int size,
            @RequestParam(name = "instrumentId", required = false) String instrumentId) {

        String ledgerId = getLedgerId(principal, ledgerSlug);
        final var pageable = PageRequest.of(page, size);

        // Load instruments for symbol lookup
        Map<String, Instrument> instrumentMap = instrumentService.listAllByLedger(ledgerId).stream()
                .collect(Collectors.toMap(Instrument::id, Function.identity()));

        Page<EntityModel<InstrumentPriceResource>> priceResources;

        if (instrumentId != null && !instrumentId.isBlank()) {
            priceResources = instrumentPriceService.listByInstrument(instrumentId, pageable)
                    .map(price -> toEntityModel(price, instrumentMap.get(price.instrumentId()), ledgerSlug, principal));
        } else {
            priceResources = instrumentPriceService.listByLedger(ledgerId, pageable)
                    .map(price -> toEntityModel(price, instrumentMap.get(price.instrumentId()), ledgerSlug, principal));
        }

        return buildPagedResponse(ledgerSlug, page, size, priceResources, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityModel<InstrumentPriceResource> createPrice(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateInstrumentPriceRequest request) {

        String ledgerId = getLedgerId(principal, ledgerSlug);

        // Verify instrument belongs to ledger
        Instrument instrument = instrumentService.findById(request.instrumentId())
                .orElseThrow(() -> new IllegalArgumentException("Instrument not found: " + request.instrumentId()));

        if (!instrument.ledgerId().equals(ledgerId)) {
            throw new IllegalArgumentException("Instrument does not belong to this ledger");
        }

        InstrumentPrice created = instrumentPriceService.savePrice(
                request.instrumentId(),
                request.price(),
                request.effectiveDate(),
                "MANUAL"
        );

        return toEntityModel(created, instrument, ledgerSlug, principal);
    }

    @DeleteMapping("/{priceId}")
    public ResponseEntity<Void> deletePrice(
            @PathVariable String ledgerSlug,
            @PathVariable String priceId,
            JwtAuthenticationToken principal) {

        instrumentPriceService.delete(priceId);
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

    private EntityModel<InstrumentPriceResource> toEntityModel(InstrumentPrice price, Instrument instrument, String ledgerSlug, JwtAuthenticationToken principal) {
        InstrumentPriceResource resource = instrumentPriceMapper.toResource(price, instrument);
        return EntityModel.of(resource);
    }

    private PagedModel<EntityModel<InstrumentPriceResource>> buildPagedResponse(
            String ledgerSlug,
            int page,
            int size,
            Page<EntityModel<InstrumentPriceResource>> priceResources,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                priceResources.getContent(),
                new PagedModel.PageMetadata(
                        priceResources.getSize(),
                        priceResources.getNumber(),
                        priceResources.getTotalElements(),
                        priceResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(InstrumentPriceController.class)
                .listPrices(ledgerSlug, principal, page, size, null)).withSelfRel());

        return pagedModel;
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
}
