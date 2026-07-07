import { useQuery } from "@tanstack/react-query"
import { getAccounts, getBalanceSummary } from "@/api/accounts"
import type { PaginationParams } from "@/api/types"

export function useAccounts(ledgerSlug: string | undefined, params?: PaginationParams) {
  return useQuery({
    queryKey: ["ledger", ledgerSlug, "accounts", params ?? {}],
    queryFn: () => getAccounts(ledgerSlug!, params),
    enabled: !!ledgerSlug,
  })
}

export function useBalanceSummary(ledgerSlug: string | undefined) {
  return useQuery({
    queryKey: ["ledger", ledgerSlug, "balance-summary"],
    queryFn: () => getBalanceSummary(ledgerSlug!),
    enabled: !!ledgerSlug,
  })
}
