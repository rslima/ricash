package com.rslima.ricash.ledgers;

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

    record DBAccount(String id, String ledgerId, String parentAccountId, String name, String description,
                     String currency, String type, String status, BigDecimal balance, Instant createdAt) {
    }

    @Override
    public Page<Account> listLedgerAccounts(String ledgerId, PageRequest pageRequest) {
        final var dbAccounts = jdbcClient.sql("""
                        SELECT
                            a.id,
                            a.ledger_id,
                            a.parent_account_id,
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
                            transaction_entries te ON a.id = te.account_id
                        WHERE
                            a.ledger_id = :ledgerId
                        GROUP BY a.id, a.ledger_id, a.parent_account_id, a.name, a.description,
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
                        SELECT
                            a.id,
                            a.ledger_id,
                            a.parent_account_id,
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
                            transaction_entries te ON a.id = te.account_id
                        WHERE
                            a.ledger_id = :ledgerId AND
                            a.id = :accountId
                        GROUP BY a.id, a.ledger_id, a.parent_account_id, a.name, a.description,
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
                        INSERT INTO accounts (id, ledger_id, parent_account_id, name, description, currency, type, status, created_at)
                        VALUES (:id, :ledgerId, :parentAccountId, :name, :description, :currency, :type, :status, :createdAt)
                        """)
                .param("id", account.id())
                .param("ledgerId", ledgerId)
                .param("parentAccountId", null)
                .param("name", account.name())
                .param("description", account.description())
                .param("currency", account.currency())
                .param("type", account.type().name())
                .param("status", account.status().name())
                .param("createdAt", Timestamp.from(account.createdAt()))
                .update();

        return account;
    }

    private Account toAccount(DBAccount dbAccount) {
        return new Account(
                dbAccount.id(),
                dbAccount.name(),
                dbAccount.description(),
                dbAccount.currency(),
                AccountType.valueOf(dbAccount.type()),
                AccountStatus.valueOf(dbAccount.status()),
                dbAccount.balance() != null ? dbAccount.balance() : BigDecimal.ZERO,
                dbAccount.createdAt(),
                new ArrayList<>()
        );
    }
}
