package com.rslima.ricash.ledgers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
@Slf4j
public class LedgerJdbcRepository implements LedgerRepository {
    private final JdbcClient jdbcClient;

    @Override
    public List<Ledger> listAll(String userId) {
        final var ledgersAndAccounts = jdbcClient.sql(
                        """
                                SELECT
                                    l.id l_id,
                                    l.user_id,
                                    l.name ledger_name,
                                    l.description ledger_description,
                                    l.currency ledger_currency,
                                    l.created_at ledger_created_at,
                                    a.id account_id,
                                    a.parent_account_id,
                                    a.name account_name,
                                    a.description account_description,
                                    a.currency account_currency,
                                    a.status,
                                    a.type,
                                    a.created_at account_created_at
                                FROM
                                    ledgers l
                                LEFT JOIN
                                    public.accounts a
                                ON
                                    l.id = a.ledger_id
                                WHERE
                                    user_id = :userId""")
                .param("userId", userId)
                .query(LedgerAndAccount.class)
                .list();

        final var dbLedgers = ledgersAndAccounts.stream().map(this::toDBLedgerAndAccount).toList();

        return dbLedgers.stream()
                .collect(groupingBy(DBLedgerAndAccount::ledger))
                .entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().stream().map(DBLedgerAndAccount::account).toList()))
                .map(e -> Map.entry(e.getKey(), buildAccountForest(e.getValue())))
                .map(e -> toLedger(e.getKey(), e.getValue()))
                .toList();
    }

    private DBLedgerAndAccount toDBLedgerAndAccount(LedgerAndAccount la) {
        final var account = new DBAccount(
                la.accountId(),
                la.lId(),
                la.parentAccountId(),
                la.accountName(),
                la.accountDescription(),
                la.accountCurrency(),
                la.type(),
                la.status(),
                la.accountCreatedAt());

        return new DBLedgerAndAccount(
                new DBLedger(
                        la.lId(),
                        la.userId(),
                        la.ledgerName(),
                        la.ledgerDescription(),
                        la.ledgerCurrency(),
                        la.ledgerCreatedAt()),
                account);
    }



    record DBLedgerAndAccount(DBLedger ledger, DBAccount account) {}


    record LedgerAndAccount(String lId, String userId, String ledgerName, String ledgerDescription,
                            String ledgerCurrency, Instant ledgerCreatedAt,
                            String accountId, String parentAccountId, String accountName, String accountDescription,
                            String accountCurrency, String status, String type,
                            Instant accountCreatedAt) {
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

    private static @NotNull Ledger toLedger(DBLedger dbLedger, List<Account> accountForest) {
        return new Ledger(
                dbLedger.id(),
                dbLedger.name(),
                dbLedger.description(),
                dbLedger.currency(),
                dbLedger.createdAt(),
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

    record DBLedger(String id, String userId, String name, String description, String currency, Instant createdAt) {
    }

    record DBAccount(String id, String ledgerId, String parentAccountId, String name, String description,
                     String currency,
                     String type, String status, Instant createdAt) {
    }


}
