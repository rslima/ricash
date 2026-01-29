package com.rslima.ricash.ledgers;

import io.vavr.Tuple2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
    public Page<Ledger> listUserLedgers(String userId, PageRequest pageRequest) {

        final var ledgersAndAccounts = jdbcClient.sql("""
                           WITH RECURSIVE account_tree AS (
                               -- Base case: each account includes itself
                               SELECT id, id as root_id, ledger_id
                               FROM accounts

                               UNION ALL

                               -- Recursive case: find children and link them to the same root
                               SELECT a.id, at.root_id, a.ledger_id
                               FROM accounts a
                               INNER JOIN account_tree at ON a.parent_account_id = at.id
                           )
                           SELECT
                               l.id l_id,
                               l.user_id,
                               l.slug ledger_slug,
                               l.name ledger_name,
                               l.description ledger_description,
                               l.currency ledger_currency,
                               l.created_at ledger_created_at,
                               ab.account_id,
                               ab.parent_account_id,
                               ab.account_slug,
                               ab.account_name,
                               ab.account_description,
                               ab.account_currency,
                               ab.status,
                               ab.type,
                               ab.account_balance,
                               ab.account_created_at
                           FROM
                               (SELECT
                                    *
                                FROM
                                    ledgers
                                WHERE
                                    user_id = :userId
                                OFFSET :offset
                                LIMIT :limit) l
                           LEFT JOIN
                               (SELECT
                                   a.id AS account_id,
                                   a.ledger_id,
                                   a.parent_account_id,
                                   a.slug AS account_slug,
                                   a.name AS account_name,
                                   a.description AS account_description,
                                   a.currency AS account_currency,
                                   a.status,
                                   a.type,
                                   a.created_at AS account_created_at,
                                   COALESCE(
                                       CASE
                                           WHEN a.type IN ('ASSET', 'EXPENSE') THEN
                                               SUM(CASE WHEN te.type = 'DEBIT' THEN te.amount ELSE 0 END) -
                                               SUM(CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END)
                                           ELSE
                                               SUM(CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END) -
                                               SUM(CASE WHEN te.type = 'DEBIT' THEN te.amount ELSE 0 END)
                                       END,
                                       0
                                   ) AS account_balance
                               FROM accounts a
                               LEFT JOIN account_tree at ON at.root_id = a.id AND at.ledger_id = a.ledger_id
                               LEFT JOIN transaction_entries te ON at.id = te.account_id
                               GROUP BY a.id, a.ledger_id, a.parent_account_id, a.slug, a.name, a.description,
                                        a.currency, a.status, a.type, a.created_at) ab
                           ON
                               l.id = ab.ledger_id""")
                .param("userId", userId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBLedgerAndAccount.class)
                .list();

        final var dbLedgers = ledgersAndAccounts.stream().map(this::toTupleLedgerAndAccount).toList();

        List<Ledger> result = dbLedgers.stream()
                .collect(groupingBy(Tuple2::_1))
                .entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue().stream()
                        .map(Tuple2::_2)
                        .filter(account -> account.id() != null)
                        .toList()))
                .map(e -> Map.entry(e.getKey(), buildAccountForest(e.getValue())))
                .map(e -> toLedger(e.getKey(), e.getValue()))
                .toList();
        return new PageImpl<>(result,
                pageRequest,
                result.size());
    }

    record DBLedgerAndAccount(String lId, String userId, String ledgerSlug, String ledgerName, String ledgerDescription,
                              String ledgerCurrency, Instant ledgerCreatedAt,
                              String accountId, String parentAccountId, String accountSlug, String accountName, String accountDescription,
                              String accountCurrency, String status, String type, BigDecimal accountBalance,
                              Instant accountCreatedAt) {
    }

    record DBLedger(String id, String userId, String slug, String name, String description, String currency, Instant createdAt) {
    }

    record DBAccount(String id, String ledgerId, String parentAccountId, String slug, String name, String description,
                     String currency,
                     String type, String status, BigDecimal balance, Instant createdAt) {
    }

    private Tuple2<DBLedger, DBAccount> toTupleLedgerAndAccount(DBLedgerAndAccount la) {
        final var dbLedger = new DBLedger(
                la.lId(),
                la.userId(),
                la.ledgerSlug(),
                la.ledgerName(),
                la.ledgerDescription(),
                la.ledgerCurrency(),
                la.ledgerCreatedAt());

        final var account = new DBAccount(
                la.accountId(),
                la.lId(),
                la.parentAccountId(),
                la.accountSlug(),
                la.accountName(),
                la.accountDescription(),
                la.accountCurrency(),
                la.type(),
                la.status(),
                la.accountBalance(),
                la.accountCreatedAt());

        return new Tuple2<>(dbLedger, account);
    }

    @Override
    public Optional<Ledger> findById(String userId, String id) {

        final var dbLedger = jdbcClient.sql("""
                        SELECT
                            id,
                            user_id,
                            slug,
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
                            WITH RECURSIVE account_tree AS (
                                -- Base case: each account includes itself
                                SELECT id, id as root_id
                                FROM accounts
                                WHERE ledger_id = :id

                                UNION ALL

                                -- Recursive case: find children and link them to the same root
                                SELECT a.id, at.root_id
                                FROM accounts a
                                INNER JOIN account_tree at ON a.parent_account_id = at.id
                                WHERE a.ledger_id = :id
                            )
                            SELECT
                                a.id,
                                a.ledger_id,
                                a.parent_account_id,
                                a.slug,
                                a.name,
                                a.description,
                                a.currency,
                                a.type,
                                a.status,
                                COALESCE(
                                    CASE
                                        WHEN a.type IN ('ASSET', 'EXPENSE') THEN
                                            SUM(CASE WHEN te.type = 'DEBIT' THEN te.amount ELSE 0 END) -
                                            SUM(CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END)
                                        ELSE
                                            SUM(CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END) -
                                            SUM(CASE WHEN te.type = 'DEBIT' THEN te.amount ELSE 0 END)
                                    END,
                                    0
                                ) AS balance,
                                a.created_at
                            FROM
                                accounts a
                            LEFT JOIN
                                account_tree at ON at.root_id = a.id
                            LEFT JOIN
                                transaction_entries te ON at.id = te.account_id
                            WHERE
                                a.ledger_id = :id
                            GROUP BY a.id, a.ledger_id, a.parent_account_id, a.slug, a.name, a.description,
                                     a.currency, a.type, a.status, a.created_at
                            """)
                    .param("id", id)
                    .query(DBAccount.class)
                    .list();

            final var accountForest = buildAccountForest(dbLedgerAccounts);

            return dbLedger.map(toLedger(accountForest));

        }

        return Optional.empty();
    }

    @Override
    public Optional<Ledger> findBySlug(String userId, String slug) {
        final var dbLedger = jdbcClient.sql("""
                        SELECT
                            id,
                            user_id,
                            slug,
                            name,
                            description,
                            currency,
                            created_at
                        FROM
                            ledgers
                        WHERE
                            user_id = :userId AND
                            slug = :slug
                        """)
                .param("userId", userId)
                .param("slug", slug)
                .query(DBLedger.class)
                .optional();

        if (dbLedger.isPresent()) {
            final var id = dbLedger.get().id();
            final var dbLedgerAccounts = jdbcClient.sql("""
                            WITH RECURSIVE account_tree AS (
                                -- Base case: each account includes itself
                                SELECT id, id as root_id
                                FROM accounts
                                WHERE ledger_id = :id

                                UNION ALL

                                -- Recursive case: find children and link them to the same root
                                SELECT a.id, at.root_id
                                FROM accounts a
                                INNER JOIN account_tree at ON a.parent_account_id = at.id
                                WHERE a.ledger_id = :id
                            )
                            SELECT
                                a.id,
                                a.ledger_id,
                                a.parent_account_id,
                                a.slug,
                                a.name,
                                a.description,
                                a.currency,
                                a.type,
                                a.status,
                                COALESCE(
                                    CASE
                                        WHEN a.type IN ('ASSET', 'EXPENSE') THEN
                                            SUM(CASE WHEN te.type = 'DEBIT' THEN te.amount ELSE 0 END) -
                                            SUM(CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END)
                                        ELSE
                                            SUM(CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END) -
                                            SUM(CASE WHEN te.type = 'DEBIT' THEN te.amount ELSE 0 END)
                                    END,
                                    0
                                ) AS balance,
                                a.created_at
                            FROM
                                accounts a
                            LEFT JOIN
                                account_tree at ON at.root_id = a.id
                            LEFT JOIN
                                transaction_entries te ON at.id = te.account_id
                            WHERE
                                a.ledger_id = :id
                            GROUP BY a.id, a.ledger_id, a.parent_account_id, a.slug, a.name, a.description,
                                     a.currency, a.type, a.status, a.created_at
                            """)
                    .param("id", id)
                    .query(DBAccount.class)
                    .list();

            final var accountForest = buildAccountForest(dbLedgerAccounts);
            return dbLedger.map(toLedger(accountForest));
        }

        return Optional.empty();
    }

    @Override
    public Ledger create(Ledger ledger) {
        jdbcClient.sql("""
                        INSERT INTO ledgers (id, user_id, slug, name, description, currency, created_at)
                        VALUES (:id, :userId, :slug, :name, :description, :currency, :createdAt)
                        """)
                .param("id", ledger.id())
                .param("userId", ledger.userId())
                .param("slug", ledger.slug())
                .param("name", ledger.name())
                .param("description", ledger.description())
                .param("currency", ledger.currency())
                .param("createdAt", Timestamp.from(ledger.createdAt()))
                .update();

        return ledger;
    }

    @Override
    public Ledger update(String userId, String slug, String name, String description) {
        jdbcClient.sql("""
                        UPDATE ledgers SET name = :name, description = :description
                        WHERE user_id = :userId AND slug = :slug
                        """)
                .param("userId", userId)
                .param("slug", slug)
                .param("name", name)
                .param("description", description)
                .update();

        return findBySlug(userId, slug).orElseThrow();
    }

    @Override
    public boolean existsBySlug(String userId, String slug) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM ledgers WHERE user_id = :userId AND slug = :slug
                        """)
                .param("userId", userId)
                .param("slug", slug)
                .query(Long.class)
                .single() > 0;
    }

    private static @NotNull Function<DBLedger, Ledger> toLedger(List<Account> accountForest) {
        return dbLedger1 -> toLedger(dbLedger1, accountForest);
    }

    private static @NotNull Ledger toLedger(DBLedger dbLedger, List<Account> accountForest) {
        return new Ledger(
                dbLedger.id(),
                dbLedger.userId(),
                dbLedger.slug(),
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
                dbAccount.slug(),
                dbAccount.name(),
                dbAccount.description(),
                dbAccount.currency(),
                AccountType.valueOf(dbAccount.type()),
                AccountStatus.valueOf(dbAccount.status()),
                dbAccount.balance() != null ? dbAccount.balance() : BigDecimal.ZERO,
                dbAccount.createdAt(),
                dbAccount.parentAccountId(),
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

}
