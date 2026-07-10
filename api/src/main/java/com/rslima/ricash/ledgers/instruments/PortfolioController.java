package com.rslima.ricash.ledgers.instruments;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.rslima.ricash.web.AuthenticatedUser.userId;
import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;

@RestController
@RequestMapping(value = "/v1/ledgers/{ledgerSlug}/portfolio", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final InstrumentPositionMapper positionMapper;

    @GetMapping
    public CollectionModel<EntityModel<InstrumentPositionResource>> getAllPositions(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {

        List<InstrumentPosition> positions = portfolioService.getAllPositions(userId(principal), ledgerSlug);

        List<EntityModel<InstrumentPositionResource>> resources = positions.stream()
                .map(positionMapper::toResource)
                .map(EntityModel::of)
                .toList();

        return CollectionModel.of(resources);
    }

    @GetMapping("/accounts/{accountId}")
    public CollectionModel<EntityModel<InstrumentPositionResource>> getAccountPositions(
            @PathVariable String ledgerSlug,
            @PathVariable String accountId,
            JwtAuthenticationToken principal) {

        List<InstrumentPosition> positions = portfolioService.getPositions(userId(principal), ledgerSlug, accountId);

        List<EntityModel<InstrumentPositionResource>> resources = positions.stream()
                .map(positionMapper::toResource)
                .map(EntityModel::of)
                .toList();

        return CollectionModel.of(resources);
    }
}
