import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import {
  getLedgers,
  createLedger,
  updateLedger,
  deleteLedger,
  type CreateLedgerData,
  type UpdateLedgerData,
} from "./ledgers"

/**
 * Query keys for ledger data. Centralizing them keeps `useQuery` and the
 * `invalidateQueries` calls in the mutations below in sync.
 */
export const ledgerKeys = {
  all: ["ledgers"] as const,
}

/** Fetches the current user's ledgers. Pass `enabled` to gate on auth. */
export function useLedgers(enabled = true) {
  return useQuery({
    queryKey: ledgerKeys.all,
    queryFn: async () => (await getLedgers()).data,
    enabled,
  })
}

export function useCreateLedger() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateLedgerData) => createLedger(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ledgerKeys.all }),
  })
}

export function useUpdateLedger() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ slug, data }: { slug: string; data: UpdateLedgerData }) =>
      updateLedger(slug, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ledgerKeys.all }),
  })
}

export function useDeleteLedger() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (slug: string) => deleteLedger(slug),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ledgerKeys.all }),
  })
}
