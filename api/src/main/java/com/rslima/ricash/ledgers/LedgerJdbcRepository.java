package com.rslima.ricash.ledgers;

import com.rslima.ricash.ledgers.accounts.Account;
import com.rslima.ricash.ledgers.accounts.AccountTreeSql;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.stream.Collectors.groupingBy;

@RequiredArgsConstructor
@Slf4j
public class LedgerJdbcRepository implements LedgerRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Page<Ledger> listUserLedgers(String userId, Pageable pageRequest) {
        final var dbLedgers = jdbcClient.sql("""
                        SELECT id, user_id, slug, name, description, currency, created_at
                        FROM ledgers
                        WHERE user_id = :userId
                        ORDER BY created_at, id
                        OFFSET :offset
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBLedger.class)
                .list();

        // One account query for the whole page, scoped to the page's ledgers.
        final Map<String, List<AccountTreeSql.DBAccount>> accountsByLedger = dbLedgers.isEmpty()
                ? Map.of()
                : jdbcClient.sql(AccountTreeSql.SELECT_ACCOUNTS_WITH_BALANCE_FOR_LEDGERS)
                        .param("ledgerIds", dbLedgers.stream().map(DBLedger::id).toList())
                        .query(AccountTreeSql.DBAccount.class)
                        .list()
                        .stream()
                        .collect(groupingBy(AccountTreeSql.DBAccount::ledgerId));

        final List<Ledger> result = dbLedgers.stream()
                .map(ledger -> toLedger(ledger,
                        buildAccountForest(accountsByLedger.getOrDefault(ledger.id(), List.of()))))
                .toList();

        final var total = jdbcClient.sql("SELECT COUNT(*) FROM ledgers WHERE user_id = :userId")
                .param("userId", userId)
                .query(Long.class)
                .single();

        return new PageImpl<>(result, pageRequest, total);
    }

    record DBLedger(String id, String userId, String slug, String name, String description, String currency, Instant createdAt) {
    }

    @Override
    public Optional<Ledger> findBySlug(String userId, String slug) {
        return findLedger(userId, "slug = :lookupValue", slug);
    }

    @Override
    public Optional<LedgerRef> findRefBySlug(String userId, String slug) {
        return jdbcClient.sql("""
                        SELECT id, slug, currency FROM ledgers
                        WHERE user_id = :userId AND slug = :slug
                        """)
                .param("userId", userId)
                .param("slug", slug)
                .query(LedgerRef.class)
                .optional();
    }

    /**
     * Loads one ledger of the user matching the given lookup condition (its
     * value bound to {@code :lookupValue}), together with its account forest.
     */
    private Optional<Ledger> findLedger(String userId, String lookupCondition, String lookupValue) {
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
                        """ + lookupCondition)
                .param("userId", userId)
                .param("lookupValue", lookupValue)
                .query(DBLedger.class)
                .optional();

        return dbLedger.map(ledger -> {
            final var dbLedgerAccounts = jdbcClient.sql(AccountTreeSql.SELECT_LEDGER_ACCOUNTS_WITH_BALANCE)
                    .param("ledgerId", ledger.id())
                    .query(AccountTreeSql.DBAccount.class)
                    .list();

            return toLedger(ledger, buildAccountForest(dbLedgerAccounts));
        });
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

    private static Ledger toLedger(DBLedger dbLedger, List<Account> accountForest) {
        return new Ledger(
                dbLedger.id(),
                dbLedger.userId(),
                dbLedger.slug(),
                dbLedger.name(),
                dbLedger.description(),
                dbLedger.currency(),
                dbLedger.createdAt(),
                accountForest);
    }

    private List<Account> buildAccountForest(List<AccountTreeSql.DBAccount> ledgerAccounts) {

        final var roots = ledgerAccounts.stream()
                .filter(a -> a.parentAccountId() == null)
                .toList();

        final var remainingAccounts = new ArrayList<>(ledgerAccounts);
        remainingAccounts.removeAll(roots);

        return buildTree(roots, remainingAccounts);
    }

    private List<Account> buildTree(List<AccountTreeSql.DBAccount> roots, ArrayList<AccountTreeSql.DBAccount> remainingAccounts) {

        final List<Account> accountForest = new ArrayList<>();

        for (var root : roots) {
            final var children = remainingAccounts.stream()
                    .filter(a -> a.parentAccountId().equals(root.id()))
                    .toList();
            remainingAccounts.removeAll(children);
            Account account = root.toAccount();
            accountForest.add(account);
            account.subAccounts().addAll(buildTree(children, remainingAccounts));
        }

        return accountForest;
    }

}
