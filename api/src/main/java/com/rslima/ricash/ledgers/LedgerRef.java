package com.rslima.ricash.ledgers;

/**
 * Lightweight ledger projection returned by the ownership check: identity and
 * currency only, without the account forest and its balance rollup. Nested
 * resource services only need the id to scope their queries.
 */
public record LedgerRef(String id, String slug, String currency) {
}
