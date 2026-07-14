package com.rslima.ricash.ledgers.transactions.export;

import com.rslima.ricash.ledgers.Ledger;
import com.rslima.ricash.ledgers.LedgerAccess;
import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountNotFoundException;
import com.rslima.ricash.ledgers.accounts.AccountType;
import com.rslima.ricash.ledgers.transactions.TransactionExportRow;
import com.rslima.ricash.ledgers.transactions.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
public class TransactionExportServiceBean implements TransactionExportService {

    private static final List<AccountType> OFX_STATEMENT_TYPES = List.of(AccountType.ASSET, AccountType.LIABILITY);

    private final TransactionRepository transactionRepository;
    private final LedgerAccess ledgerAccess;

    // REPEATABLE_READ (Postgres snapshot isolation) makes the entry list and
    // the OFX balance query read the same committed state, so an exported
    // statement's transactions always reconcile with its LEDGERBAL.
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExportFile export(String userId, String ledgerSlug, ExportRequest request) {
        final var format = ExportFormat.parse(request.format());
        requireSaneDate("from", request.from());
        requireSaneDate("to", request.to());
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to())) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }

        final var ledger = ledgerAccess.requireLedger(userId, ledgerSlug);
        final var accountsById = flattenAccountForest(ledger.accounts());

        Account scopeRoot = null;
        List<Account> scope = null;
        if (request.accountId() != null) {
            scopeRoot = accountsById.get(request.accountId());
            if (scopeRoot == null) {
                throw new AccountNotFoundException(request.accountId());
            }
            scope = request.includeChildren() ? flattenSubtree(scopeRoot) : List.of(scopeRoot);
        }

        final var toExclusive = request.to() == null ? null : request.to().plusDays(1);
        final var rows = transactionRepository.listEntriesForExport(
                ledger.id(),
                scope == null ? null : scope.stream().map(Account::id).toList(),
                request.from(),
                toExclusive);

        final var content = switch (format) {
            case CSV -> CsvTransactionExporter.generate(rows);
            case OFX -> generateOfx(ledger, accountsById, scopeRoot, scope, rows, request, toExclusive);
        };

        return new ExportFile(filename(ledger, scopeRoot, request, format), format.contentType(), content);
    }

    private byte[] generateOfx(Ledger ledger, Map<String, Account> accountsById, Account scopeRoot,
                               List<Account> scope, List<TransactionExportRow> rows,
                               ExportRequest request, LocalDate toExclusive) {
        final List<Account> statementAccounts;
        if (scopeRoot != null) {
            if (!OFX_STATEMENT_TYPES.contains(scopeRoot.type())) {
                throw new IllegalArgumentException(
                        "OFX export is only supported for ASSET and LIABILITY accounts");
            }
            statementAccounts = scope.stream()
                    .filter(account -> OFX_STATEMENT_TYPES.contains(account.type()))
                    .toList();
        } else {
            // Ledger-wide: one statement per asset/liability account with activity.
            final var activeAccountIds = rows.stream().map(TransactionExportRow::accountId).distinct().toList();
            statementAccounts = activeAccountIds.stream()
                    .map(accountsById::get)
                    .filter(account -> account != null && OFX_STATEMENT_TYPES.contains(account.type()))
                    .toList();
        }

        final var rowsByAccountId = rows.stream().collect(groupingBy(TransactionExportRow::accountId));
        final var balances = transactionRepository.getAccountBalancesAsOf(
                ledger.id(), statementAccounts.stream().map(Account::id).toList(), toExclusive);

        return OfxTransactionExporter.generate(
                statementAccounts, rowsByAccountId, request.from(), request.to(), balances, Instant.now());
    }

    /**
     * ISO parsing accepts expanded years (+999999999-12-31 = LocalDate.MAX),
     * which would overflow plusDays and the JDBC date bind into a 500. Reject
     * anything outside the plain four-digit-year range as bad input.
     */
    private static void requireSaneDate(String name, LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new IllegalArgumentException("'" + name + "' must have a year between 1 and 9999");
        }
    }

    private static String filename(Ledger ledger, Account scopeRoot, ExportRequest request, ExportFormat format) {
        final String range;
        if (request.from() == null && request.to() == null) {
            range = "all";
        } else {
            range = (request.from() != null ? request.from().toString() : "start")
                    + "_" + (request.to() != null ? request.to().toString() : "end");
        }
        final var accountPart = scopeRoot != null ? scopeRoot.slug() + "-" : "";
        return ledger.slug() + "-" + accountPart + "transactions-" + range + "." + format.extension();
    }

    private static Map<String, Account> flattenAccountForest(Collection<Account> forest) {
        final var accountsById = new LinkedHashMap<String, Account>();
        collectAccounts(forest, accountsById);
        return accountsById;
    }

    private static void collectAccounts(Collection<Account> accounts, Map<String, Account> accountsById) {
        if (accounts == null) {
            return;
        }
        for (var account : accounts) {
            accountsById.put(account.id(), account);
            collectAccounts(account.subAccounts(), accountsById);
        }
    }

    private static List<Account> flattenSubtree(Account root) {
        return List.copyOf(flattenAccountForest(List.of(root)).values());
    }
}
