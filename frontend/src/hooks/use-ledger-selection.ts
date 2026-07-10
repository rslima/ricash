import { useMemo, useState } from "react"
import { useParams } from "react-router-dom"
import { useAuth } from "@/contexts/AuthContext"
import { useLedgers } from "@/api/ledgers.hooks"
import { DEFAULT_CURRENCY } from "@/lib/utils"

/**
 * Owns the ledger-picker state every ledger-scoped page repeats: the ledgers
 * query, the selected slug (route param → explicit choice → first ledger,
 * derived rather than set in an effect), the selected ledger and its currency.
 * The query's loading/error state is exposed so pages can fold it into their
 * aggregate signals.
 */
export function useLedgerSelection() {
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const [chosenSlug, setChosenSlug] = useState<string | null>(ledgerSlug || null)

  const ledgersQuery = useLedgers(isAuthenticated)
  const ledgers = useMemo(() => ledgersQuery.data?.data ?? [], [ledgersQuery.data])

  const selectedLedgerSlug = chosenSlug ?? ledgers[0]?.attributes.slug ?? null
  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)
  const ledgerCurrency = selectedLedger?.attributes.currency ?? DEFAULT_CURRENCY

  return {
    ledgers,
    selectedLedgerSlug,
    setSelectedLedgerSlug: setChosenSlug,
    selectedLedger,
    ledgerCurrency,
    isLoading: ledgersQuery.isLoading,
    isError: ledgersQuery.isError,
    error: ledgersQuery.error,
  }
}
