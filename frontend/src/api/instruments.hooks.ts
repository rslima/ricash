import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PaginationParams } from "./types"
import {
  getInstruments,
  getAllInstruments,
  getInstrument,
  getInstrumentPrices,
  getPortfolio,
  getAccountPortfolio,
  createInstrument,
  updateInstrument,
  deleteInstrument,
  createInstrumentPrice,
  deleteInstrumentPrice,
  type CreateInstrumentInput,
  type UpdateInstrumentInput,
  type CreateInstrumentPriceInput,
} from "./instruments"

/**
 * Instrument query keys, scoped by ledger. Prices and portfolio positions live
 * under this prefix, so mutating an instrument or a price (which both move
 * portfolio valuations) invalidates the whole ledger subtree.
 */
export const instrumentKeys = {
  all: ["instruments"] as const,
  ledger: (ledgerSlug: string) => ["instruments", ledgerSlug] as const,
  list: (ledgerSlug: string, params?: PaginationParams) =>
    ["instruments", ledgerSlug, "list", params ?? {}] as const,
  allList: (ledgerSlug: string) => ["instruments", ledgerSlug, "all"] as const,
  detail: (ledgerSlug: string, instrumentId: string) =>
    ["instruments", ledgerSlug, "detail", instrumentId] as const,
  prices: (ledgerSlug: string, params?: PaginationParams & { instrumentId?: string }) =>
    ["instruments", ledgerSlug, "prices", params ?? {}] as const,
  portfolio: (ledgerSlug: string) => ["instruments", ledgerSlug, "portfolio"] as const,
  accountPortfolio: (ledgerSlug: string, accountId: string) =>
    ["instruments", ledgerSlug, "portfolio", "account", accountId] as const,
}

export function useInstruments(ledgerSlug: string, params?: PaginationParams, enabled = true) {
  return useQuery({
    queryKey: instrumentKeys.list(ledgerSlug, params),
    queryFn: () => getInstruments(ledgerSlug, params),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useAllInstruments(ledgerSlug: string, enabled = true) {
  return useQuery({
    queryKey: instrumentKeys.allList(ledgerSlug),
    queryFn: () => getAllInstruments(ledgerSlug),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useInstrument(ledgerSlug: string, instrumentId: string, enabled = true) {
  return useQuery({
    queryKey: instrumentKeys.detail(ledgerSlug, instrumentId),
    queryFn: () => getInstrument(ledgerSlug, instrumentId),
    enabled: enabled && !!ledgerSlug && !!instrumentId,
  })
}

export function useInstrumentPrices(
  ledgerSlug: string,
  params?: PaginationParams & { instrumentId?: string },
  enabled = true
) {
  return useQuery({
    queryKey: instrumentKeys.prices(ledgerSlug, params),
    queryFn: () => getInstrumentPrices(ledgerSlug, params),
    enabled: enabled && !!ledgerSlug,
  })
}

export function usePortfolio(ledgerSlug: string, enabled = true) {
  return useQuery({
    queryKey: instrumentKeys.portfolio(ledgerSlug),
    queryFn: () => getPortfolio(ledgerSlug),
    enabled: enabled && !!ledgerSlug,
  })
}

export function useAccountPortfolio(ledgerSlug: string, accountId: string, enabled = true) {
  return useQuery({
    queryKey: instrumentKeys.accountPortfolio(ledgerSlug, accountId),
    queryFn: () => getAccountPortfolio(ledgerSlug, accountId),
    enabled: enabled && !!ledgerSlug && !!accountId,
  })
}

export function useCreateInstrument(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateInstrumentInput) => createInstrument(ledgerSlug, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: instrumentKeys.ledger(ledgerSlug) }),
  })
}

export function useUpdateInstrument(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ instrumentId, input }: { instrumentId: string; input: UpdateInstrumentInput }) =>
      updateInstrument(ledgerSlug, instrumentId, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: instrumentKeys.ledger(ledgerSlug) }),
  })
}

export function useDeleteInstrument(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (instrumentId: string) => deleteInstrument(ledgerSlug, instrumentId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: instrumentKeys.ledger(ledgerSlug) }),
  })
}

export function useCreateInstrumentPrice(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateInstrumentPriceInput) => createInstrumentPrice(ledgerSlug, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: instrumentKeys.ledger(ledgerSlug) }),
  })
}

export function useDeleteInstrumentPrice(ledgerSlug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (priceId: string) => deleteInstrumentPrice(ledgerSlug, priceId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: instrumentKeys.ledger(ledgerSlug) }),
  })
}
