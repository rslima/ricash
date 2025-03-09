package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@RequiredArgsConstructor
@Slf4j
public class LedgerJdbcRepository implements LedgerRepository {
    private final JdbcClient jdbcClient;

    @Override
    public List<Ledger> listAll(String userId) {
        return List.of();
    }

    @Override
    public Optional<Ledger> findById(String userId, String id) {

        final var dbLedger = jdbcClient.sql("""
                                   SELECT
                                       id,
                                       user_id,
                                       name,
                                       description,
                                       currency,
                                       created_at
                                   FROM
                                       ledgers
                                   WHERE
                                       user_id = :userId AND
                                       id = :id
                                   """)
                .param("userId", userId)
                .param("id", id)
                .query(DBLedger.class)
                .optional();

        if (dbLedger.isPresent()) {
            final var dbLedgerAccounts = jdbcClient.sql("""
                    SELECT
                        id,
                        ledger_id,
                        parent_account_id,
                        name,
                        description,
                        currency,
                        type,
                        status,
                        created_at
                    FROM
                        accounts
                    WHERE
                        ledger_id = :id
                    """)
                    .param("id", id)
                    .query(DBAccount.class)
                    .list();

            final var accountForest = buildAccountForest(dbLedgerAccounts);

            return dbLedger.map(toLedger(accountForest));

        }

        return Optional.empty();
    }

    private static @NotNull Function<DBLedger, Ledger> toLedger(List<Account> accountForest) {
        return dbLedger1 -> toLedger(dbLedger1, accountForest);
    }

    private static @NotNull Ledger toLedger(DBLedger dbLedger1, List<Account> accountForest) {
        return new Ledger(
                dbLedger1.id(),
                dbLedger1.name(),
                dbLedger1.description(),
                dbLedger1.currency(),
                dbLedger1.createdAt(),
                accountForest,
                List.of());
    }

    private static @NotNull Account toAccount(DBAccount dbAccount) {
        return new Account(
                dbAccount.id(),
                dbAccount.name(),
                dbAccount.description(),
                dbAccount.currency(),
                AccountType.valueOf(dbAccount.type()),
                AccountStatus.valueOf(dbAccount.status()),
                dbAccount.createdAt(),
                new ArrayList<>());
    }

    private List<Account> buildAccountForest(List<DBAccount> ledgerAccounts) {

        final var roots = ledgerAccounts.stream()
                .filter(a -> a.parentAccountId() == null)
                .toList();

        final var remainingAccounts = new ArrayList<>(ledgerAccounts);
        remainingAccounts.removeAll(roots);

        return buildTree(roots, remainingAccounts);
    }

    private List<Account> buildTree(List<DBAccount> roots, ArrayList<DBAccount> remainingAccounts) {

        final List<Account> accountForest = new ArrayList<>();

        for (var root : roots) {
            final var children = remainingAccounts.stream()
                    .filter(a -> a.parentAccountId().equals(root.id()))
                    .toList();
            remainingAccounts.removeAll(children);
            Account account = toAccount(root);
            accountForest.add(account);
            account.subAccounts().addAll(buildTree(children, remainingAccounts));
        }

        return accountForest;
    }

    record DBLedger(String id, String userId, String name, String description, String currency, Instant createdAt) {}
    
    record DBAccount(String id, String ledgerId, String parentAccountId, String name, String description, String currency,
                    String type, String status, Instant createdAt) {}
    
}
