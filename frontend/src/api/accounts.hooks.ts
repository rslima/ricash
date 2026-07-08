import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PaginationParams } from "./types"
import {
  getAccounts,
  getAccount,
  getBalanceSummary,
  createAccount,
  updateAccount,
  deleteAccount,
  type CreateAccountData,
  type UpdateAccountData,
} from "./accounts"

/**
 * Account query keys, scoped by ledger so different ledgers cache separately.
 * `ledger(slug)` is the invalidation prefix — invalidating it refreshes every
 * account list/detail/balance query for that ledger.
 */
export const accountKeys = {
  all: ["accounts"] as const,
  ledger: (ledgerSlug: string) => ["accounts", ledgerSlug] as const,
  list: (ledgerSlug: string, params?: PaginationParams) =>
    ["accounts", ledgerSlug, "list", params ?? {}] as const,
  detail: (ledgerSlug: string, accountId: string) =>
    ["accounts", ledgerSlug, "detail", accountId] as const,
  balanceSummary: (ledgerSlug: string) => ["accounts", ledgerSlug, "balance-summary"] as const,
}

export function useAccounts(ledgerSlug: string, params?: PaginationParams, enabled = true) {
  return useQuery({
    queryKey: accountKeys.list(ledgerSlug, params),
    queryFn: () => getAccounts(ledgerSlug, params),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useAccount(ledgerSlug: string, accountId: string, enabled = true) {
  return useQuery({
    queryKey: accountKeys.detail(ledgerSlug, accountId),
    queryFn: () => getAccount(ledgerSlug, accountId),
    enabled: enabled && !!ledgerSlug && !!accountId,
  })
}

export function useBalanceSummary(ledgerSlug: string, enabled = true) {
  return useQuery({
    queryKey: accountKeys.balanceSummary(ledgerSlug),
    queryFn: () => getBalanceSummary(ledgerSlug),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useCreateAccount(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateAccountData) => createAccount(ledgerSlug, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.ledger(ledgerSlug) }),
  })
}

export function useUpdateAccount(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, data }: { accountId: string; data: UpdateAccountData }) =>
      updateAccount(ledgerSlug, accountId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.ledger(ledgerSlug) }),
  })
}

export function useDeleteAccount(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (accountId: string) => deleteAccount(ledgerSlug, accountId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.ledger(ledgerSlug) }),
  })
}
