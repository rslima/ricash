import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  createLedger,
  deleteLedger,
  getLedgers,
  updateLedger,
  type CreateLedgerData,
  type UpdateLedgerData,
} from "@/api/ledgers"
import type { PaginationParams } from "@/api/types"

export const ledgersKey = ["ledgers"] as const

export function useLedgers(params?: PaginationParams) {
  return useQuery({
    queryKey: params ? [...ledgersKey, params] : ledgersKey,
    queryFn: () => getLedgers(params),
  })
}

export function useCreateLedger() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateLedgerData) => createLedger(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ledgersKey }),
  })
}

export function useUpdateLedger() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, data }: { slug: string; data: UpdateLedgerData }) => updateLedger(slug, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ledgersKey }),
  })
}

export function useDeleteLedger() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteLedger(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ledgersKey }),
  })
}
