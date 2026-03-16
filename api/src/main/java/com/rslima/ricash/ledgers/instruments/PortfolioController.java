package com.rslima.ricash.ledgers.instruments;

import com.rslima.ricash.ledgers.LedgerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;

@RestController
@RequestMapping(value = "/api/v1/ledgers/{ledgerSlug}/portfolio", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final InstrumentPositionMapper positionMapper;
    private final LedgerService ledgerService;

    @GetMapping
    public CollectionModel<EntityModel<InstrumentPositionResource>> getAllPositions(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {

        String ledgerId = getLedgerId(principal, ledgerSlug);
        List<InstrumentPosition> positions = portfolioService.getAllPositions(ledgerId);

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

        String ledgerId = getLedgerId(principal, ledgerSlug);
        List<InstrumentPosition> positions = portfolioService.getPositions(ledgerId, accountId);

        List<EntityModel<InstrumentPositionResource>> resources = positions.stream()
                .map(positionMapper::toResource)
                .map(EntityModel::of)
                .toList();

        return CollectionModel.of(resources);
    }

    private String getLedgerId(JwtAuthenticationToken principal, String ledgerSlug) {
        return ledgerService.findBySlug(getUserId(principal), ledgerSlug)
                .orElseThrow(() -> new IllegalArgumentException("Ledger not found: " + ledgerSlug))
                .id();
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }
}
