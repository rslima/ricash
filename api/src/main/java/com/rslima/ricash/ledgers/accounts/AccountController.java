package com.rslima.ricash.ledgers.accounts;

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

import org.springframework.http.MediaType;

import static com.toedter.spring.hateoas.jsonapi.MediaTypes.JSON_API_VALUE;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping(value = "/v1/ledgers/{ledgerSlug}/accounts", produces = JSON_API_VALUE)
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @GetMapping
    public PagedModel<EntityModel<AccountResource>> listAccounts(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "20") int size) {

        final var pageable = PageRequest.of(page, size);
        var accountResources = accountService.listLedgerAccounts(getUserId(principal), ledgerSlug, pageable)
                .map(account -> toEntityModel(ledgerSlug, account, principal));

        return buildPagedAccountResponse(ledgerSlug, page, size, accountResources, principal);
    }

    private static @Nullable String getUserId(JwtAuthenticationToken principal) {
        return principal.getName();
    }

    @GetMapping("/{accountId}")
    public EntityModel<AccountResource> getAccount(
            @PathVariable String ledgerSlug,
            @PathVariable String accountId,
            JwtAuthenticationToken principal) {

        final var account = accountService.find(getUserId(principal), ledgerSlug, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return toEntityModel(ledgerSlug, account, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<AccountResource>> createAccount(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateAccountRequest request) {

        Account createdAccount = accountService.create(getUserId(principal), ledgerSlug, request);
        EntityModel<AccountResource> entityModel = toEntityModel(ledgerSlug, createdAccount, principal);

        return ResponseEntity
                .created(linkTo(methodOn(AccountController.class).getAccount(ledgerSlug, createdAccount.id(), principal)).toUri())
                .body(entityModel);
    }

    @PutMapping("/{accountId}")
    public EntityModel<AccountResource> updateAccount(
            @PathVariable String ledgerSlug,
            @PathVariable String accountId,
            JwtAuthenticationToken principal,
            @Valid @RequestBody UpdateAccountRequest request) {

        Account updatedAccount = accountService.update(getUserId(principal), ledgerSlug, accountId, request);
        return toEntityModel(ledgerSlug, updatedAccount, principal);
    }

    @GetMapping(value = "/balance-summary", produces = { MediaType.APPLICATION_JSON_VALUE, JSON_API_VALUE })
    public ResponseEntity<BalanceSummaryResource> getBalanceSummary(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {

        var summary = accountService.getBalanceSummary(getUserId(principal), ledgerSlug);
        var resource = new BalanceSummaryResource(ledgerSlug, summary.balanceByCurrency());
        return ResponseEntity.ok(resource);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable String ledgerSlug,
            @PathVariable String accountId,
            JwtAuthenticationToken principal) {

        accountService.delete(getUserId(principal), ledgerSlug, accountId);
        return ResponseEntity.noContent().build();
    }

    private EntityModel<AccountResource> toEntityModel(String ledgerSlug, Account account, JwtAuthenticationToken principal) {
        AccountResource resource = accountMapper.toResource(account);
        EntityModel<AccountResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(AccountController.class).getAccount(ledgerSlug, account.id(), principal)).withSelfRel());
        return entityModel;
    }

    private PagedModel<EntityModel<AccountResource>> buildPagedAccountResponse(
            String ledgerSlug,
            int page,
            int size,
            Page<EntityModel<AccountResource>> accountResources,
            JwtAuthenticationToken principal) {

        var pagedModel = PagedModel.of(
                accountResources.getContent(),
                new PagedModel.PageMetadata(
                        accountResources.getSize(),
                        accountResources.getNumber(),
                        accountResources.getTotalElements(),
                        accountResources.getTotalPages()));

        pagedModel.add(linkTo(methodOn(AccountController.class).listAccounts(ledgerSlug, principal, page, size)).withSelfRel());
        pagedModel.add(linkTo(methodOn(AccountController.class).listAccounts(ledgerSlug, principal, 0, size)).withRel("first"));

        if (accountResources.getTotalPages() > 0) {
            pagedModel.add(linkTo(methodOn(AccountController.class).listAccounts(
                    ledgerSlug,
                    principal,
                    accountResources.getTotalPages() - 1,
                    size)).withRel("last"));
        }
        if (accountResources.hasNext()) {
            pagedModel.add(linkTo(methodOn(AccountController.class).listAccounts(
                    ledgerSlug,
                    principal,
                    accountResources.getNumber() + 1,
                    size)).withRel("next"));
        }
        if (accountResources.hasPrevious()) {
            pagedModel.add(linkTo(methodOn(AccountController.class).listAccounts(
                    ledgerSlug,
                    principal,
                    accountResources.getNumber() - 1,
                    size)).withRel("prev"));
        }

        return pagedModel;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<JsonApiErrors> handleAccountNotFoundException(AccountNotFoundException ex) {
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

    @ExceptionHandler(AccountHasTransactionsException.class)
    public ResponseEntity<JsonApiErrors> handleAccountHasTransactionsException(AccountHasTransactionsException ex) {
        return ResponseEntity.status(CONFLICT).body(
                JsonApiErrors.create().withError(
                        JsonApiError.create()
                                .withStatus(Integer.toString(CONFLICT.value()))
                                .withTitle(CONFLICT.getReasonPhrase())
                                .withDetail(ex.getMessage())));
    }
}
