import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query"
import {
  getTransactions,
  getCategoryTransactions,
  getMonthlyReport,
  getMonthlyExpenseBreakdown,
  getMonthlyIncomeBreakdown,
  getTransactionDescriptions,
  getTransactionTemplates,
  createTransaction,
  updateTransaction,
  deleteTransaction,
  type TransactionFilters,
  type CreateTransactionData,
  type UpdateTransactionData,
} from "./transactions"

/**
 * Transaction query keys, scoped by ledger. Monthly reports/breakdowns and
 * descriptions/templates all live under this prefix so a single ledger-level
 * invalidation refreshes lists and reports together.
 */
export const transactionKeys = {
  all: ["transactions"] as const,
  ledger: (ledgerSlug: string) => ["transactions", ledgerSlug] as const,
  list: (ledgerSlug: string, filters?: TransactionFilters) =>
    ["transactions", ledgerSlug, "list", filters ?? {}] as const,
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

// A transaction changes account balances and envelope balances, so mutations
// invalidate those resources for the ledger in addition to transactions/reports.
function invalidateTransactionEffects(queryClient: QueryClient, ledgerSlug: string) {
  queryClient.invalidateQueries({ queryKey: transactionKeys.ledger(ledgerSlug) })
  queryClient.invalidateQueries({ queryKey: ["accounts", ledgerSlug] })
  queryClient.invalidateQueries({ queryKey: ["envelopes", ledgerSlug] })
}

export function useTransactions(ledgerSlug: string, filters?: TransactionFilters, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.list(ledgerSlug, filters),
    queryFn: () => getTransactions(ledgerSlug, filters),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useCategoryTransactions(
  ledgerSlug: string,
  accountId: string,
  year: number,
  month: number,
  enabled = true
) {
  return useQuery({
    queryKey: transactionKeys.category(ledgerSlug, accountId, year, month),
    queryFn: () => getCategoryTransactions(ledgerSlug, accountId, year, month),
    enabled: enabled && !!ledgerSlug && !!accountId,
  })
}

export function useMonthlyReport(ledgerSlug: string, year: number, month: number, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.monthlyReport(ledgerSlug, year, month),
    queryFn: () => getMonthlyReport(ledgerSlug, year, month),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useMonthlyExpenseBreakdown(ledgerSlug: string, year: number, month: number, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.expenseBreakdown(ledgerSlug, year, month),
    queryFn: () => getMonthlyExpenseBreakdown(ledgerSlug, year, month),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useMonthlyIncomeBreakdown(ledgerSlug: string, year: number, month: number, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.incomeBreakdown(ledgerSlug, year, month),
    queryFn: () => getMonthlyIncomeBreakdown(ledgerSlug, year, month),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useTransactionDescriptions(ledgerSlug: string, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.descriptions(ledgerSlug),
    queryFn: () => getTransactionDescriptions(ledgerSlug),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useTransactionTemplates(ledgerSlug: string, enabled = true) {
  return useQuery({
    queryKey: transactionKeys.templates(ledgerSlug),
    queryFn: () => getTransactionTemplates(ledgerSlug),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useCreateTransaction(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateTransactionData) => createTransaction(ledgerSlug, data),
    onSuccess: () => invalidateTransactionEffects(queryClient, ledgerSlug),
  })
}

export function useUpdateTransaction(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ transactionId, data }: { transactionId: string; data: UpdateTransactionData }) =>
      updateTransaction(ledgerSlug, transactionId, data),
    onSuccess: () => invalidateTransactionEffects(queryClient, ledgerSlug),
  })
}

export function useDeleteTransaction(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (transactionId: string) => deleteTransaction(ledgerSlug, transactionId),
    onSuccess: () => invalidateTransactionEffects(queryClient, ledgerSlug),
  })
}
