package com.rslima.ricash.ledgers.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

/**
 * SQL fragments shared by the JDBC repositories that read accounts with their
 * balances. An account's balance aggregates the {@code account_balance_summary}
 * rows of its whole subtree, resolved through a recursive account-tree CTE.
 */
public final class AccountTreeSql {

    private AccountTreeSql() {
    }

    /**
     * Recursive CTE linking every account of a ledger (param {@code :ledgerId})
     * to itself and all of its descendants via {@code root_id}.
     */
    public static final String LEDGER_ACCOUNT_TREE_CTE = """
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
            """;

    /**
     * Signed balance over {@code account_balance_summary bs}: debits increase
     * ASSET/EXPENSE accounts, credits increase all other types. Expects
     * {@code accounts} aliased as {@code a}.
     */
    public static final String BALANCE_EXPRESSION = """
            COALESCE(
                CASE
                    WHEN a.type IN ('ASSET', 'EXPENSE') THEN
                        SUM(bs.debit_total) - SUM(bs.credit_total)
                    ELSE
                        SUM(bs.credit_total) - SUM(bs.debit_total)
                END,
                0
            )""";

    /** {@link #BALANCE_EXPRESSION} projected as the {@code balance} column. */
    public static final String BALANCE_COLUMN = BALANCE_EXPRESSION + " AS balance,\n";

    /** GROUP BY matching the account columns selected alongside {@link #BALANCE_COLUMN}. */
    public static final String ACCOUNT_GROUP_BY = """
            GROUP BY a.id, a.ledger_id, a.parent_account_id, a.slug, a.name, a.description,
                     a.currency, a.type, a.status, a.created_at
            """;

    /**
     * Selects every account of a ledger (param {@code :ledgerId}) with its
     * aggregated subtree balance. Callers may append ORDER BY / OFFSET / LIMIT
     * clauses. Rows map to {@link DBAccount}.
     */
    public static final String SELECT_LEDGER_ACCOUNTS_WITH_BALANCE = LEDGER_ACCOUNT_TREE_CTE
            + """
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
            + BALANCE_COLUMN
            + """
                a.created_at
            FROM
                accounts a
            LEFT JOIN
                account_tree at ON at.root_id = a.id
            LEFT JOIN
                account_balance_summary bs ON bs.account_id = at.id AND bs.currency = a.currency
            WHERE
                a.ledger_id = :ledgerId
            """
            + ACCOUNT_GROUP_BY;

    /** Row shape produced by the shared account/balance queries. */
    public record DBAccount(String id, String ledgerId, String parentAccountId, String slug, String name,
                            String description, String currency, String type, String status, BigDecimal balance,
                            Instant createdAt) {

        public Account toAccount() {
            return new Account(
                    id,
                    slug,
                    name,
                    description,
                    currency,
                    AccountType.valueOf(type),
                    AccountStatus.valueOf(status),
                    balance != null ? balance : BigDecimal.ZERO,
                    createdAt,
                    parentAccountId,
                    new ArrayList<>());
        }
    }
}
