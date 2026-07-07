package com.rslima.ricash.ledgers.transactions;

import com.rslima.ricash.configuration.JsonApiPagination;

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
import org.springframework.http.MediaType;
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
@RequestMapping(value = "/v1/ledgers/{ledgerSlug}/transactions", produces = JSON_API_VALUE)
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
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "description", required = false) String description,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month) {

        final var pageable = PageRequest.of(page, size);
        Page<EntityModel<TransactionResource>> transactionResources;

        if (accountId != null && !accountId.isBlank() && year != null && month != null) {
            // Drill-down from the monthly report: all transactions in the
            // category and its subcategories for the given month.
            transactionResources = transactionService.listCategoryTransactions(principal.getName(), ledgerSlug, accountId, year, month, pageable)
                    .map(transaction -> toEntityModel(transaction, ledgerSlug, principal));
        } else if (accountId != null && !accountId.isBlank()) {
            transactionResources = transactionService.listAccountTransactions(principal.getName(), ledgerSlug, accountId, pageable)
                    .map(transaction -> toEntityModel(transaction, ledgerSlug, principal));
        } else if (description != null && !description.isBlank()) {
            transactionResources = transactionService.searchByDescription(principal.getName(), ledgerSlug, description, pageable)
                    .map(transaction -> toEntityModel(transaction, ledgerSlug, principal));
        } else {
            transactionResources = transactionService.listLedgerTransactions(principal.getName(), ledgerSlug, pageable)
                    .map(transaction -> toEntityModel(transaction, ledgerSlug, principal));
        }

        return JsonApiPagination.pagedModel(transactionResources,
                p -> methodOn(TransactionController.class).listTransactions(ledgerSlug, principal, p, size, null, null, null, null));
    }

    @GetMapping("/descriptions")
    public java.util.List<String> getDistinctDescriptions(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {
        return transactionService.getDistinctDescriptions(principal.getName(), ledgerSlug);
    }

    @GetMapping("/templates")
    public org.springframework.hateoas.CollectionModel<EntityModel<TransactionResource>> getTransactionTemplates(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal) {
        java.util.List<EntityModel<TransactionResource>> resources = transactionService
                .getTransactionTemplates(principal.getName(), ledgerSlug)
                .stream()
                .map(transaction -> toEntityModel(transaction, ledgerSlug, principal))
                .toList();
        return org.springframework.hateoas.CollectionModel.of(resources);
    }

    @GetMapping(value = "/monthly-report", produces = { MediaType.APPLICATION_JSON_VALUE, JSON_API_VALUE })
    public ResponseEntity<MonthlyReportResource> getMonthlyReport(
            @PathVariable String ledgerSlug,
            @RequestParam int year,
            @RequestParam int month,
            JwtAuthenticationToken principal) {

        var report = transactionService.getMonthlyReport(principal.getName(), ledgerSlug, year, month);
        var resource = new MonthlyReportResource(
                String.format("%d-%02d", year, month),
                report.year(),
                report.month(),
                report.incomeByCurrency(),
                report.expensesByCurrency()
        );
        return ResponseEntity.ok(resource);
    }

    @GetMapping(value = "/monthly-expense-breakdown", produces = { MediaType.APPLICATION_JSON_VALUE, JSON_API_VALUE })
    public ResponseEntity<MonthlyExpenseBreakdownResource> getMonthlyExpenseBreakdown(
            @PathVariable String ledgerSlug,
            @RequestParam int year,
            @RequestParam int month,
            JwtAuthenticationToken principal) {

        var breakdown = transactionService.getMonthlyExpenseBreakdown(principal.getName(), ledgerSlug, year, month);
        var resource = new MonthlyExpenseBreakdownResource(
                String.format("%d-%02d", year, month),
                breakdown.year(),
                breakdown.month(),
                breakdown.expensesByAccountId()
        );
        return ResponseEntity.ok(resource);
    }

    @GetMapping(value = "/monthly-income-breakdown", produces = { MediaType.APPLICATION_JSON_VALUE, JSON_API_VALUE })
    public ResponseEntity<MonthlyIncomeBreakdownResource> getMonthlyIncomeBreakdown(
            @PathVariable String ledgerSlug,
            @RequestParam int year,
            @RequestParam int month,
            JwtAuthenticationToken principal) {

        var breakdown = transactionService.getMonthlyIncomeBreakdown(principal.getName(), ledgerSlug, year, month);
        var resource = new MonthlyIncomeBreakdownResource(
                String.format("%d-%02d", year, month),
                breakdown.year(),
                breakdown.month(),
                breakdown.incomeByAccountId()
        );
        return ResponseEntity.ok(resource);
    }

    @GetMapping("/category-transactions")
    public PagedModel<EntityModel<CategoryTransactionResource>> listCategoryTransactionAmounts(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @RequestParam(name = "accountId") String accountId,
            @RequestParam(name = "year") int year,
            @RequestParam(name = "month") int month,
            @RequestParam(name = "page[number]", required = false, defaultValue = "0") int page,
            @RequestParam(name = "page[size]", required = false, defaultValue = "200") int size) {

        final var pageable = PageRequest.of(page, size);
        Page<EntityModel<CategoryTransactionResource>> resources = transactionService
                .listCategoryTransactionAmounts(principal.getName(), ledgerSlug, accountId, year, month, pageable)
                .map(transaction -> EntityModel.of(toCategoryResource(transaction)));

        return JsonApiPagination.pagedModel(resources,
                p -> methodOn(TransactionController.class)
                        .listCategoryTransactionAmounts(ledgerSlug, principal, accountId, year, month, p, size));
    }

    @GetMapping("/{transactionId}")
    public EntityModel<TransactionResource> getTransaction(
            @PathVariable String ledgerSlug,
            @PathVariable String transactionId,
            JwtAuthenticationToken principal) {

        final var transaction = transactionService.find(principal.getName(), ledgerSlug, transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        return toEntityModel(transaction, ledgerSlug, principal);
    }

    @PostMapping
    public ResponseEntity<EntityModel<TransactionResource>> createTransaction(
            @PathVariable String ledgerSlug,
            JwtAuthenticationToken principal,
            @Valid @RequestBody CreateTransactionRequest request) {

        Transaction created = transactionService.create(principal.getName(), ledgerSlug, request);
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

        Transaction updated = transactionService.update(principal.getName(), ledgerSlug, transactionId, request);
        return toEntityModel(updated, ledgerSlug, principal);
    }

    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable String ledgerSlug,
            @PathVariable String transactionId,
            JwtAuthenticationToken principal) {

        transactionService.delete(principal.getName(), ledgerSlug, transactionId);
        return ResponseEntity.noContent().build();
    }

    private CategoryTransactionResource toCategoryResource(CategoryTransaction transaction) {
        return new CategoryTransactionResource(
                transaction.id(),
                transaction.date(),
                transaction.description(),
                transaction.amount(),
                transaction.currency());
    }

    private EntityModel<TransactionResource> toEntityModel(Transaction transaction, String ledgerSlug, JwtAuthenticationToken principal) {
        TransactionResource resource = transactionMapper.toResource(transaction);
        EntityModel<TransactionResource> entityModel = EntityModel.of(resource);
        entityModel.add(linkTo(methodOn(TransactionController.class)
                .getTransaction(ledgerSlug, transaction.id(), principal)).withSelfRel());
        return entityModel;
    }

}
