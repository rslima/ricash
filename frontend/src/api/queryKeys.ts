import type { PaginationParams } from "./types"
import type { TransactionFilters } from "./transactions"

// Every query key lives in this module, so hooks files and pages depend on it
// instead of on each other (a mutation in one hooks file often invalidates
// another resource's keys).

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

/** Ledgers belong to the user, so their keys are not ledger-scoped. */
export const ledgerKeys = {
  all: ["ledgers"] as const,
}

/** Exchange rates are user-global (not ledger-scoped). */
export const exchangeRateKeys = {
  all: ["exchange-rates"] as const,
  list: (params?: PaginationParams) => ["exchange-rates", "list", params ?? {}] as const,
}

/**
 * Account query keys, scoped by ledger so different ledgers cache separately.
 * `ledger(slug)` is the invalidation prefix — invalidating it refreshes every
 * account list/detail/balance query for that ledger.
 */
export const accountKeys = {
  ...createLedgerScopedKeys("accounts"),
  balanceSummary: (ledgerSlug: string) => ["accounts", ledgerSlug, "balance-summary"] as const,
}

/**
 * Envelope query keys, scoped by ledger. Budget summary and envelope→account
 * mappings live under this prefix so a ledger-level invalidation refreshes them
 * along with the envelope lists.
 */
export const envelopeKeys = {
  ...createLedgerScopedKeys("envelopes"),
  balance: (ledgerSlug: string, envelopeId: string, year: number, month: number) =>
    ["envelopes", ledgerSlug, "balance", envelopeId, year, month] as const,
  accounts: (ledgerSlug: string, envelopeId: string) =>
    ["envelopes", ledgerSlug, "accounts", envelopeId] as const,
  budget: (ledgerSlug: string, year: number, month: number) =>
    ["envelopes", ledgerSlug, "budget", year, month] as const,
  mappings: (ledgerSlug: string) => ["envelopes", ledgerSlug, "mappings"] as const,
}

/**
 * Instrument query keys, scoped by ledger. Prices and portfolio positions live
 * under this prefix, so mutating an instrument or a price (which both move
 * portfolio valuations) invalidates the whole ledger subtree.
 */
export const instrumentKeys = {
  ...createLedgerScopedKeys("instruments"),
  allList: (ledgerSlug: string) => ["instruments", ledgerSlug, "all"] as const,
  prices: (ledgerSlug: string, params?: PaginationParams & { instrumentId?: string }) =>
    ["instruments", ledgerSlug, "prices", params ?? {}] as const,
  portfolio: (ledgerSlug: string) => ["instruments", ledgerSlug, "portfolio"] as const,
  accountPortfolio: (ledgerSlug: string, accountId: string) =>
    ["instruments", ledgerSlug, "portfolio", "account", accountId] as const,
}

/**
 * Transaction query keys, scoped by ledger. Monthly reports/breakdowns and
 * descriptions/templates all live under this prefix so a single ledger-level
 * invalidation refreshes lists and reports together.
 */
export const transactionKeys = {
  ...createLedgerScopedKeys<"transactions", TransactionFilters>("transactions"),
  category: (ledgerSlug: string, accountId: string, year: number, month: number) =>
    ["transactions", ledgerSlug, "category", accountId, year, month] as const,
  monthlyReport: (ledgerSlug: string, year: number, month: number) =>
    ["transactions", ledgerSlug, "monthly-report", year, month] as const,
  expenseBreakdown: (ledgerSlug: string, year: number, month: number) =>
    ["transactions", ledgerSlug, "expense-breakdown", year, month] as const,
  incomeBreakdown: (ledgerSlug: string, year: number, month: number) =>
    ["transactions", ledgerSlug, "income-breakdown", year, month] as const,
  descriptions: (ledgerSlug: string) => ["transactions", ledgerSlug, "descriptions"] as const,
  templates: (ledgerSlug: string) => ["transactions", ledgerSlug, "templates"] as const,
}
