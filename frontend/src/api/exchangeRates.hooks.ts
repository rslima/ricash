import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { exchangeRateKeys } from "./queryKeys"
import type { PaginationParams } from "./types"
import {
  getExchangeRates,
  createExchangeRate,
  deleteExchangeRate,
  fetchExchangeRate,
  type CreateExchangeRateInput,
  type FetchExchangeRateInput,
} from "./exchangeRates"

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

/** Force-fetches a rate from the external provider, then refreshes the list. */
export function useFetchExchangeRate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: FetchExchangeRateInput) => fetchExchangeRate(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: exchangeRateKeys.all }),
  })
}
