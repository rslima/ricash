import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { PaginationParams } from "./types"
import {
  getExchangeRates,
  createExchangeRate,
  deleteExchangeRate,
  type CreateExchangeRateInput,
} from "./exchangeRates"

/** Exchange rates are user-global (not ledger-scoped). */
export const exchangeRateKeys = {
  all: ["exchange-rates"] as const,
  list: (params?: PaginationParams) => ["exchange-rates", "list", params ?? {}] as const,
}

export function useExchangeRates(params?: PaginationParams, enabled = true) {
  return useQuery({
    queryKey: exchangeRateKeys.list(params),
    queryFn: () => getExchangeRates(params),
    enabled,
  })
}

export function useCreateExchangeRate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: CreateExchangeRateInput) => createExchangeRate(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: exchangeRateKeys.all }),
  })
}

export function useDeleteExchangeRate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteExchangeRate(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: exchangeRateKeys.all }),
  })
}
