import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query"
import { accountKeys, transactionKeys } from "./queryKeys"
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

// Transaction lists and reports embed account names and group by account
// type, so changing or removing an account refreshes them too.
function invalidateAccountEffects(queryClient: QueryClient, ledgerSlug: string) {
  queryClient.invalidateQueries({ queryKey: accountKeys.ledger(ledgerSlug) })
  queryClient.invalidateQueries({ queryKey: transactionKeys.ledger(ledgerSlug) })
}

export function useUpdateAccount(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, data }: { accountId: string; data: UpdateAccountData }) =>
      updateAccount(ledgerSlug, accountId, data),
    onSuccess: () => invalidateAccountEffects(queryClient, ledgerSlug),
  })
}

export function useDeleteAccount(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (accountId: string) => deleteAccount(ledgerSlug, accountId),
    onSuccess: () => invalidateAccountEffects(queryClient, ledgerSlug),
  })
}
