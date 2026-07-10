package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.web.PagedModels;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import static com.rslima.ricash.web.AuthenticatedUser.userId;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(value = "/v1/ledgers/{ledgerSlug}/instrument-prices", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class InstrumentPriceController {

    private final InstrumentPriceService instrumentPriceService;
    private final InstrumentService instrumentService;
    private final InstrumentPriceMapper instrumentPriceMapper;

    @GetMapping
    public PagedModel<EntityModel<InstrumentPriceResource>> listPrices(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "50") int size,
            @RequestParam(name = "instrumentId", required = false) String instrumentId) {

        final var userId = userId(principal);
        final var pageable = PageRequest.of(page, size);

        // Load instruments for symbol lookup
        Map<String, Instrument> instrumentMap = instrumentService.listAllByLedger(userId, ledgerSlug).stream()
                .collect(Collectors.toMap(Instrument::id, Function.identity()));

        Page<EntityModel<InstrumentPriceResource>> priceResources;

        if (instrumentId != null && !instrumentId.isBlank()) {
            priceResources = instrumentPriceService.listByInstrument(userId, ledgerSlug, instrumentId, pageable)
                    .map(price -> toEntityModel(price, instrumentMap.get(price.instrumentId()), ledgerSlug, principal));
        } else {
            priceResources = instrumentPriceService.listByLedger(userId, ledgerSlug, pageable)
                    .map(price -> toEntityModel(price, instrumentMap.get(price.instrumentId()), ledgerSlug, principal));
        }

        return PagedModels.toPagedModel(priceResources,
                p -> methodOn(InstrumentPriceController.class).listPrices(ledgerSlug, principal, p, size, instrumentId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityModel<InstrumentPriceResource> createPrice(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateInstrumentPriceRequest request) {

        final var userId = userId(principal);

        Instrument instrument = instrumentService.find(userId, ledgerSlug, request.instrumentId())
                .orElseThrow(() -> new InstrumentNotFoundException(request.instrumentId()));

        InstrumentPrice created = instrumentPriceService.savePrice(
                userId,
                ledgerSlug,
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

        instrumentPriceService.delete(userId(principal), ledgerSlug, priceId);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<InstrumentPriceResource> toEntityModel(InstrumentPrice price, Instrument instrument, String ledgerSlug, JwtAuthenticationToken principal) {
        InstrumentPriceResource resource = instrumentPriceMapper.toResource(price, instrument);
        return EntityModel.of(resource);
    }
}
