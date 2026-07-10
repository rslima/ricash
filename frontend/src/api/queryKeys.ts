import type { PaginationParams } from "./types"

/**
 * Common query-key prefix for ledger-scoped resources. `ledger(slug)` is the
 * invalidation prefix — invalidating it refreshes every query for that
 * resource in that ledger. Domain factories spread this and add their extra
 * entries (which must keep the same `[resource, ledgerSlug, ...]` prefix).
 */
export function createLedgerScopedKeys<R extends string, P = PaginationParams>(resource: R) {
  return {
    all: [resource] as const,
    ledger: (ledgerSlug: string) => [resource, ledgerSlug] as const,
    list: (ledgerSlug: string, params?: P) => [resource, ledgerSlug, "list", params ?? {}] as const,
    detail: (ledgerSlug: string, id: string) => [resource, ledgerSlug, "detail", id] as const,
  }
}
