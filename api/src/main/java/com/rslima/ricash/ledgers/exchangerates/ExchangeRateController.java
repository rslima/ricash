package com.rslima.ricash.ledgers.exchangerates;

import com.rslima.ricash.web.PagedModels;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping(value = "/v1/exchange-rates", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateMapper exchangeRateMapper;
    private final ExchangeRateService exchangeRateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntityModel<ExchangeRateResource> createExchangeRate(
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateExchangeRateRequest request) {

        var saved = exchangeRateService.saveManualRate(
                request.fromCurrency(), request.toCurrency(), request.rate(), request.effectiveDate());
        return toEntityModel(exchangeRateMapper.toResource(saved));
    }

    @PostMapping("/fetch")
    @ResponseStatus(HttpStatus.CREATED)
    public EntityModel<ExchangeRateResource> fetchExchangeRate(
            JwtAuthenticationToken principal,
            @Valid @RequestBody FetchExchangeRateRequest request) {

        log.info("Fetching exchange rate from external provider: {} -> {} on {}",
                request.fromCurrency(), request.toCurrency(), request.date());

        var saved = exchangeRateService
                .refreshRate(request.fromCurrency(), request.toCurrency(), request.date())
                .orElseThrow(() -> new ExchangeRateNotAvailableException(
                        request.fromCurrency(), request.toCurrency(), request.date()));

        return toEntityModel(exchangeRateMapper.toResource(saved));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExchangeRate(
            JwtAuthenticationToken principal,
            @PathVariable String id) {
        exchangeRateService.deleteRate(id);
    }

    @GetMapping
    public PagedModel<EntityModel<ExchangeRateResource>> listExchangeRates(
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        var exchangeRates = exchangeRateService.listRates(pageable)
                .map(exchangeRateMapper::toResource)
                .map(this::toEntityModel);

        return PagedModels.toPagedModel(exchangeRates,
                p -> methodOn(ExchangeRateController.class).listExchangeRates(principal, p, size));
    }

    private EntityModel<ExchangeRateResource> toEntityModel(ExchangeRateResource resource) {
        return EntityModel.of(resource);
    }
}
