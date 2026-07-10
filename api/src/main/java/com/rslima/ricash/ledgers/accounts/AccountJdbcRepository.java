package com.rslima.ricash.ledgers.accounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class AccountJdbcRepository implements AccountRepository {
    private final JdbcClient jdbcClient;

    @Override
    public Page<Account> listLedgerAccounts(String ledgerId, Pageable pageRequest) {
        final var dbAccounts = jdbcClient.sql(AccountTreeSql.SELECT_LEDGER_ACCOUNTS_WITH_BALANCE + """
                        ORDER BY a.name
                        OFFSET :offset
                        LIMIT :limit
                        """)
                .param("ledgerId", ledgerId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(AccountTreeSql.DBAccount.class)
                .list();

        final var total = jdbcClient.sql("""
                        SELECT COUNT(*) FROM accounts WHERE ledger_id = :ledgerId
                        """)
                .param("ledgerId", ledgerId)
                .query(Long.class)
                .single();

        List<Account> accounts = dbAccounts.stream()
                .map(AccountTreeSql.DBAccount::toAccount)
                .toList();

        return new PageImpl<>(accounts, pageRequest, total);
    }

    @Override
    public Optional<Account> findById(String ledgerId, String accountId) {
        return jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            -- Base case: the account itself
                            SELECT id
                            FROM accounts
                            WHERE ledger_id = :ledgerId AND id = :accountId

                            UNION ALL

                            -- Recursive case: find all descendants
                            SELECT a.id
                            FROM accounts a
                            INNER JOIN account_tree at ON a.parent_account_id = at.id
                            WHERE a.ledger_id = :ledgerId
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
                        """
                        + AccountTreeSql.BALANCE_COLUMN
                        + """
                            a.created_at
                        FROM
                            accounts a
                        LEFT JOIN
                            account_tree at ON true
                        LEFT JOIN
                            account_balance_summary bs ON bs.account_id = at.id AND bs.currency = a.currency
                        WHERE
                            a.ledger_id = :ledgerId AND
                            a.id = :accountId
                        """
                        + AccountTreeSql.ACCOUNT_GROUP_BY)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .query(AccountTreeSql.DBAccount.class)
                .optional()
                .map(AccountTreeSql.DBAccount::toAccount);
    }

    @Override
    public List<AccountRef> findRefsByIds(String ledgerId, java.util.Collection<String> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT id, name, currency FROM accounts
                        WHERE ledger_id = :ledgerId AND id IN (:accountIds)
                        """)
                .param("ledgerId", ledgerId)
                .param("accountIds", List.copyOf(accountIds))
                .query(AccountRef.class)
                .list();
    }

    @Override
    public Account create(String ledgerId, Account account) {
        jdbcClient.sql("""
                        INSERT INTO accounts (id, ledger_id, parent_account_id, slug, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentAccountId, :slug, :name, :description, :currency, :type, :status, :createdAt)
                        """)
                .param("id", account.id())
                .param("ledgerId", ledgerId)
                .param("parentAccountId", account.parentAccountId())
                .param("slug", account.slug())
                .param("name", account.name())
                .param("description", account.description())
                .param("currency", account.currency())
                .param("type", account.type().name())
                .param("status", account.status().name())
                .param("createdAt", Timestamp.from(account.createdAt()))
                .update();

        return account;
    }

    @Override
    public Account update(String ledgerId, String accountId, String name, String description, AccountType type, String currency, String parentAccountId) {
        jdbcClient.sql("""
                        UPDATE accounts SET name = :name, description = :description, type = :type, currency = :currency, parent_account_id = :parentAccountId
                        WHERE ledger_id = :ledgerId AND id = :accountId
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .param("name", name)
                .param("description", description)
                .param("type", type.name())
                .param("currency", currency)
                .param("parentAccountId", parentAccountId)
                .update();

        return findById(ledgerId, accountId).orElseThrow();
    }

    @Override
    public boolean existsBySlug(String ledgerId, String slug) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM accounts WHERE ledger_id = :ledgerId AND slug = :slug
                        """)
                .param("ledgerId", ledgerId)
                .param("slug", slug)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public List<String> findChildAccountIds(String ledgerId, String accountId) {
        return jdbcClient.sql("""
                        SELECT id FROM accounts WHERE ledger_id = :ledgerId AND parent_account_id = :accountId
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .query(String.class)
                .list();
    }

    @Override
    public boolean hasTransactions(String accountId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM transaction_entries WHERE account_id = :accountId
                        """)
                .param("accountId", accountId)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public void delete(String ledgerId, String accountId) {
        jdbcClient.sql("""
                        DELETE FROM accounts WHERE ledger_id = :ledgerId AND id = :accountId
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .update();
    }

    record DBBalanceEntry(String currency, BigDecimal netBalance) {
    }

    @Override
    public BalanceSummary getBalanceSummary(String ledgerId) {
        var entries = jdbcClient.sql("""
                        SELECT
                            a.currency,
                            COALESCE(
                                SUM(bs.debit_total) - SUM(bs.credit_total),
                                0
                            ) AS net_balance
                        FROM accounts a
                        LEFT JOIN account_balance_summary bs ON bs.account_id = a.id AND bs.currency = a.currency
                        WHERE a.ledger_id = :ledgerId
                          AND a.type IN ('ASSET', 'LIABILITY')
                          AND NOT EXISTS (
                              SELECT 1 FROM accounts c
                              WHERE c.parent_account_id = a.id AND c.ledger_id = :ledgerId
                          )
                        GROUP BY a.currency
                        """)
                .param("ledgerId", ledgerId)
                .query(DBBalanceEntry.class)
                .list();

        Map<String, BigDecimal> balanceByCurrency = new HashMap<>();
        for (var entry : entries) {
            balanceByCurrency.put(entry.currency(), entry.netBalance());
        }
        return new BalanceSummary(balanceByCurrency);
    }
}
