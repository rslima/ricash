package com.rslima.ricash.ledgers.accounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class AccountJdbcRepository implements AccountRepository {
    private final JdbcClient jdbcClient;

    record DBAccount(String id, String ledgerId, String parentAccountId, String slug, String name, String description,
                     String currency, String type, String status, BigDecimal balance, Instant createdAt) {
    }

    @Override
    public Page<Account> listLedgerAccounts(String ledgerId, PageRequest pageRequest) {
        final var dbAccounts = jdbcClient.sql("""
                        WITH RECURSIVE account_tree AS (
                            -- Base case: each account includes itself
                            SELECT id, id as root_id
                            FROM accounts
                            WHERE ledger_id = :ledgerId

                            UNION ALL

                            -- Recursive case: find children and link them to the same root
                            SELECT a.id, at.root_id
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
                            COALESCE(
                                CASE
                                    WHEN a.type IN ('ASSET', 'EXPENSE') THEN
                                        SUM(CASE WHEN te.type = 'DEBIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END) -
                                        SUM(CASE WHEN te.type = 'CREDIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END)
                                    ELSE
                                        SUM(CASE WHEN te.type = 'CREDIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END) -
                                        SUM(CASE WHEN te.type = 'DEBIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END)
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
                            a.ledger_id = :ledgerId
                        GROUP BY a.id, a.ledger_id, a.parent_account_id, a.slug, a.name, a.description,
                                 a.currency, a.type, a.status, a.created_at
                        ORDER BY a.name
                        OFFSET :offset
                        LIMIT :limit
                        """)
                .param("ledgerId", ledgerId)
                .param("offset", pageRequest.getOffset())
                .param("limit", pageRequest.getPageSize())
                .query(DBAccount.class)
                .list();

        final var total = jdbcClient.sql("""
                        SELECT COUNT(*) FROM accounts WHERE ledger_id = :ledgerId
                        """)
                .param("ledgerId", ledgerId)
                .query(Long.class)
                .single();

        List<Account> accounts = dbAccounts.stream()
                .map(this::toAccount)
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
                            COALESCE(
                                CASE
                                    WHEN a.type IN ('ASSET', 'EXPENSE') THEN
                                        SUM(CASE WHEN te.type = 'DEBIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END) -
                                        SUM(CASE WHEN te.type = 'CREDIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END)
                                    ELSE
                                        SUM(CASE WHEN te.type = 'CREDIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END) -
                                        SUM(CASE WHEN te.type = 'DEBIT' THEN
                                            CASE
                                                WHEN te.to_currency = a.currency THEN te.to_amount
                                                WHEN te.currency = a.currency THEN te.amount
                                                ELSE 0
                                            END
                                        ELSE 0 END)
                                END,
                                0
                            ) AS balance,
                            a.created_at
                        FROM
                            accounts a
                        LEFT JOIN
                            account_tree at ON true
                        LEFT JOIN
                            transaction_entries te ON at.id = te.account_id
                        WHERE
                            a.ledger_id = :ledgerId AND
                            a.id = :accountId
                        GROUP BY a.id, a.ledger_id, a.parent_account_id, a.slug, a.name, a.description,
                                 a.currency, a.type, a.status, a.created_at
                        """)
                .param("ledgerId", ledgerId)
                .param("accountId", accountId)
                .query(DBAccount.class)
                .optional()
                .map(this::toAccount);
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

    private Account toAccount(DBAccount dbAccount) {
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
                new ArrayList<>()
        );
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
}
