import type { ReactNode } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderHook, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { useCreateTransaction, useDeleteTransaction, useUpdateTransaction } from "./transactions.hooks"
import { useDeleteAccount, useUpdateAccount } from "./accounts.hooks"
import { accountKeys, instrumentKeys, transactionKeys } from "./queryKeys"

vi.mock("./transactions", () => ({
  createTransaction: vi.fn().mockResolvedValue({}),
  updateTransaction: vi.fn().mockResolvedValue({}),
  deleteTransaction: vi.fn().mockResolvedValue(undefined),
}))

vi.mock("./accounts", () => ({
  updateAccount: vi.fn().mockResolvedValue({}),
  deleteAccount: vi.fn().mockResolvedValue(undefined),
}))

const LEDGER = "main"

/** [name, hook, variables]: each hook takes its own variables shape, hence `never`. */
type MutationCase = readonly [
  name: string,
  useHook: () => { mutate: (variables: never) => void; isSuccess: boolean },
  variables: unknown,
]

let queryClient: QueryClient

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

/** Seeds a query under `key` and reports whether a mutation marked it stale. */
function seed(key: readonly unknown[]) {
  queryClient.setQueryData(key, { seeded: true })
  return () => queryClient.getQueryState(key)?.isInvalidated ?? false
}

beforeEach(() => {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
})

describe("transaction mutations", () => {
  it.each<MutationCase>([
    ["create", () => useCreateTransaction(LEDGER), {}],
    ["update", () => useUpdateTransaction(LEDGER), { transactionId: "tx-1", data: {} }],
    ["delete", () => useDeleteTransaction(LEDGER), "tx-1"],
  ])("%s refreshes portfolio positions, including per-account ones", async (_name, useHook, variables) => {
    const portfolioStale = seed(instrumentKeys.portfolio(LEDGER))
    const accountPortfolioStale = seed(instrumentKeys.accountPortfolio(LEDGER, "acc-1"))
    const otherLedgerStale = seed(instrumentKeys.portfolio("other"))

    const { result } = renderHook(useHook, { wrapper })
    result.current.mutate(variables as never)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(portfolioStale()).toBe(true)
    expect(accountPortfolioStale()).toBe(true)
    expect(otherLedgerStale()).toBe(false)
  })
})

describe("account mutations", () => {
  it.each<MutationCase>([
    ["update", () => useUpdateAccount(LEDGER), { accountId: "acc-1", data: {} }],
    ["delete", () => useDeleteAccount(LEDGER), "acc-1"],
  ])("%s refreshes transaction lists and reports, which embed account names", async (_name, useHook, variables) => {
    const accountsStale = seed(accountKeys.list(LEDGER))
    const transactionsStale = seed(transactionKeys.list(LEDGER))
    const reportStale = seed(transactionKeys.monthlyReport(LEDGER, 2026, 1))

    const { result } = renderHook(useHook, { wrapper })
    result.current.mutate(variables as never)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(accountsStale()).toBe(true)
    expect(transactionsStale()).toBe(true)
    expect(reportStale()).toBe(true)
  })
})
