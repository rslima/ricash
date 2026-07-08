package com.rslima.ricash.ledgers.exchangerates;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(value = "/v1/exchange-rates", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateMapper exchangeRateMapper;

    public record CreateExchangeRateRequest(
            @NotBlank String fromCurrency,
            @NotBlank String toCurrency,
            @NotNull @Positive BigDecimal rate,
            @NotNull LocalDate effectiveDate
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityModel<ExchangeRateResource> createExchangeRate(
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateExchangeRateRequest request) {

        log.info("Creating exchange rate: {} -> {} = {} on {}",
                request.fromCurrency(), request.toCurrency(), request.rate(), request.effectiveDate());

        var exchangeRate = new ExchangeRate(
                UuidCreator.getTimeOrderedEpoch().toString(),
                request.fromCurrency().toUpperCase(),
                request.toCurrency().toUpperCase(),
                request.rate(),
                request.effectiveDate(),
                "MANUAL",
                Instant.now()
        );

        var saved = exchangeRateRepository.save(exchangeRate);
        return toEntityModel(exchangeRateMapper.toResource(saved));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchangeRate(
            JwtAuthenticationToken principal,
            @PathVariable String id) {
        log.info("Deleting exchange rate: {}", id);
        exchangeRateRepository.deleteById(id);
    }

    @GetMapping
    public PagedModel<EntityModel<ExchangeRateResource>> listExchangeRates(
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        var exchangeRates = exchangeRateRepository.findAll(pageable)
                .map(exchangeRateMapper::toResource)
                .map(this::toEntityModel);

        return buildPagedResponse(page, size, exchangeRates, principal);
    }

    private EntityModel<ExchangeRateResource> toEntityModel(ExchangeRateResource resource) {
        return EntityModel.of(resource);
    }

    private PagedModel<EntityModel<ExchangeRateResource>> buildPagedResponse(
            int page,
            int size,
            Page<EntityModel<ExchangeRateResource>> exchangeRates,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                exchangeRates.getContent(),
                new PagedModel.PageMetadata(
                        exchangeRates.getSize(),
                        exchangeRates.getNumber(),
                        exchangeRates.getTotalElements(),
                        exchangeRates.getTotalPages()));

        pagedModel.add(linkTo(methodOn(ExchangeRateController.class).listExchangeRates(principal, page, size)).withSelfRel());
        pagedModel.add(linkTo(methodOn(ExchangeRateController.class).listExchangeRates(principal, 0, size)).withRel("first"));

        if (exchangeRates.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(ExchangeRateController.class).listExchangeRates(
                    principal,
                    exchangeRates.getTotalPages() - 1,
                    size)).withRel("last"));
        }
        if (exchangeRates.hasNext()) {
            pagedModel.add(linkTo(methodOn(ExchangeRateController.class).listExchangeRates(
                    principal,
                    exchangeRates.getNumber() + 1,
                    size)).withRel("next"));
        }
        if (exchangeRates.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(ExchangeRateController.class).listExchangeRates(
                    principal,
                    exchangeRates.getNumber() - 1,
                    size)).withRel("prev"));
        }

        return pagedModel;
    }
}
