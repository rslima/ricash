package com.rslima.ricash.ledgers;

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

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/api/v1/ledgers/{ledgerSlug}/transactions", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @GetMapping
    public PagedModel<EntityModel<TransactionResource>> listTransactions(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size,
            @RequestParam(name = "accountId", required = false) String accountId) {

        final var pageable = PageRequest.of(page, size);
        Page<EntityModel<TransactionResource>> transactionResources;

        if (accountId != null && !accountId.isBlank()) {
            transactionResources = transactionService.listAccountTransactions(getUserId(principal), ledgerSlug, accountId, pageable)
                    .map(transaction -> toEntityModel(transaction, ledgerSlug, principal));
        } else {
            transactionResources = transactionService.listLedgerTransactions(getUserId(principal), ledgerSlug, pageable)
                    .map(transaction -> toEntityModel(transaction, ledgerSlug, principal));
        }

        return buildPagedResponse(ledgerSlug, page, size, transactionResources, principal);
    }

    @GetMapping("/{transactionId}")
    public EntityModel<TransactionResource> getTransaction(
            @PathVariable String ledgerSlug,
            @PathVariable String transactionId,
            JwtAuthenticationToken principal) {

        final var transaction = transactionService.find(getUserId(principal), ledgerSlug, transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        return toEntityModel(transaction, ledgerSlug, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<TransactionResource>> createTransaction(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateTransactionRequest request) {

        Transaction created = transactionService.create(getUserId(principal), ledgerSlug, request);
        EntityModel<TransactionResource> entityModel = toEntityModel(created, ledgerSlug, principal);

        return ResponseEntity
                .created(linkTo(methodOn(TransactionController.class)
                        .getTransaction(ledgerSlug, created.id(), principal)).toUri())
                .body(entityModel);
    }

    @PutMapping("/{transactionId}")
    public EntityModel<TransactionResource> updateTransaction(
            @PathVariable String ledgerSlug,
            @PathVariable String transactionId,
            JwtAuthenticationToken principal,
            @Valid @RequestBody UpdateTransactionRequest request) {

        Transaction updated = transactionService.update(getUserId(principal), ledgerSlug, transactionId, request);
        return toEntityModel(updated, ledgerSlug, principal);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable String ledgerSlug,
            @PathVariable String transactionId,
            JwtAuthenticationToken principal) {

        transactionService.delete(getUserId(principal), ledgerSlug, transactionId);
        return ResponseEntity.noContent().build();
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }

    private EntityModel<TransactionResource> toEntityModel(Transaction transaction, String ledgerSlug, JwtAuthenticationToken principal) {
        TransactionResource resource = transactionMapper.toResource(transaction);
        EntityModel<TransactionResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(TransactionController.class)
                .getTransaction(ledgerSlug, transaction.id(), principal)).withSelfRel());
        return entityModel;
    }

    private PagedModel<EntityModel<TransactionResource>> buildPagedResponse(
            String ledgerSlug,
            int page,
            int size,
            Page<EntityModel<TransactionResource>> transactionResources,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                transactionResources.getContent(),
                new PagedModel.PageMetadata(
                        transactionResources.getSize(),
                        transactionResources.getNumber(),
                        transactionResources.getTotalElements(),
                        transactionResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(TransactionController.class)
                .listTransactions(ledgerSlug, principal, page, size, null)).withSelfRel());
        pagedModel.add(linkTo(methodOn(TransactionController.class)
                .listTransactions(ledgerSlug, principal, 0, size, null)).withRel("first"));

        if (transactionResources.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(TransactionController.class).listTransactions(
                    ledgerSlug,
                    principal,
                    transactionResources.getTotalPages() - 1,
                    size,
                    null)).withRel("last"));
        }
        if (transactionResources.hasNext()) {
            pagedModel.add(linkTo(methodOn(TransactionController.class).listTransactions(
                    ledgerSlug,
                    principal,
                    transactionResources.getNumber() + 1,
                    size,
                    null)).withRel("next"));
        }
        if (transactionResources.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(TransactionController.class).listTransactions(
                    ledgerSlug,
                    principal,
                    transactionResources.getNumber() - 1,
                    size,
                    null)).withRel("prev"));
        }

        return pagedModel;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleTransactionNotFoundException(TransactionNotFoundException ex) {
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
}
