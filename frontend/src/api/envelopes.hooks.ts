import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PaginationParams } from "./types"
import { accountKeys } from "./accounts.hooks"
import {
  getEnvelopes,
  getEnvelope,
  getEnvelopeBalance,
  getEnvelopeAccounts,
  getBudgetSummary,
  getEnvelopeMappings,
  createEnvelope,
  updateEnvelope,
  deleteEnvelope,
  allocateEnvelope,
  setEnvelopeAccounts,
  type CreateEnvelopeData,
  type UpdateEnvelopeData,
  type AllocateEnvelopeData,
} from "./envelopes"

/**
 * Envelope query keys, scoped by ledger. Budget summary and envelope→account
 * mappings live under this prefix so a ledger-level invalidation refreshes them
 * along with the envelope lists.
 */
export const envelopeKeys = {
  all: ["envelopes"] as const,
  ledger: (ledgerSlug: string) => ["envelopes", ledgerSlug] as const,
  list: (ledgerSlug: string, params?: PaginationParams) =>
    ["envelopes", ledgerSlug, "list", params ?? {}] as const,
  detail: (ledgerSlug: string, envelopeId: string) =>
    ["envelopes", ledgerSlug, "detail", envelopeId] as const,
  balance: (ledgerSlug: string, envelopeId: string, year: number, month: number) =>
    ["envelopes", ledgerSlug, "balance", envelopeId, year, month] as const,
  accounts: (ledgerSlug: string, envelopeId: string) =>
    ["envelopes", ledgerSlug, "accounts", envelopeId] as const,
  budget: (ledgerSlug: string, year: number, month: number) =>
    ["envelopes", ledgerSlug, "budget", year, month] as const,
  mappings: (ledgerSlug: string) => ["envelopes", ledgerSlug, "mappings"] as const,
}

export function useEnvelopes(ledgerSlug: string, params?: PaginationParams, enabled = true) {
  return useQuery({
    queryKey: envelopeKeys.list(ledgerSlug, params),
    queryFn: () => getEnvelopes(ledgerSlug, params),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useEnvelope(ledgerSlug: string, envelopeId: string, enabled = true) {
  return useQuery({
    queryKey: envelopeKeys.detail(ledgerSlug, envelopeId),
    queryFn: () => getEnvelope(ledgerSlug, envelopeId),
    enabled: enabled && !!ledgerSlug && !!envelopeId,
  })
}

export function useEnvelopeBalance(
  ledgerSlug: string,
  envelopeId: string,
  year: number,
  month: number,
  enabled = true
) {
  return useQuery({
    queryKey: envelopeKeys.balance(ledgerSlug, envelopeId, year, month),
    queryFn: () => getEnvelopeBalance(ledgerSlug, envelopeId, year, month),
    enabled: enabled && !!ledgerSlug && !!envelopeId,
  })
}

export function useEnvelopeAccounts(ledgerSlug: string, envelopeId: string, enabled = true) {
  return useQuery({
    queryKey: envelopeKeys.accounts(ledgerSlug, envelopeId),
    queryFn: () => getEnvelopeAccounts(ledgerSlug, envelopeId),
    enabled: enabled && !!ledgerSlug && !!envelopeId,
  })
}

export function useBudgetSummary(ledgerSlug: string, year: number, month: number, enabled = true) {
  return useQuery({
    queryKey: envelopeKeys.budget(ledgerSlug, year, month),
    queryFn: () => getBudgetSummary(ledgerSlug, year, month),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useEnvelopeMappings(ledgerSlug: string, enabled = true) {
  return useQuery({
    queryKey: envelopeKeys.mappings(ledgerSlug),
    queryFn: () => getEnvelopeMappings(ledgerSlug),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useCreateEnvelope(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateEnvelopeData) => createEnvelope(ledgerSlug, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: envelopeKeys.ledger(ledgerSlug) }),
  })
}

export function useUpdateEnvelope(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ envelopeId, data }: { envelopeId: string; data: UpdateEnvelopeData }) =>
      updateEnvelope(ledgerSlug, envelopeId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: envelopeKeys.ledger(ledgerSlug) }),
  })
}

export function useDeleteEnvelope(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (envelopeId: string) => deleteEnvelope(ledgerSlug, envelopeId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: envelopeKeys.ledger(ledgerSlug) }),
  })
}

export function useAllocateEnvelope(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ envelopeId, data }: { envelopeId: string; data: AllocateEnvelopeData }) =>
      allocateEnvelope(ledgerSlug, envelopeId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: envelopeKeys.ledger(ledgerSlug) }),
  })
}

export function useSetEnvelopeAccounts(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ envelopeId, accountIds }: { envelopeId: string; accountIds: string[] }) =>
      setEnvelopeAccounts(ledgerSlug, envelopeId, accountIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: envelopeKeys.ledger(ledgerSlug) })
      // The envelope↔account mapping also drives account-side views.
      queryClient.invalidateQueries({ queryKey: accountKeys.ledger(ledgerSlug) })
    },
  })
}
