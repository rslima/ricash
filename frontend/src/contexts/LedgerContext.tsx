import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react"
import { useParams } from "react-router-dom"
import { useLedgers } from "@/api/queries/ledgers"
import type { LedgerResource } from "@/api/types"

const STORAGE_KEY = "ricash.ledger"

interface LedgerContextType {
  ledgers: LedgerResource[]
  currentLedger: LedgerResource | null
  setCurrentLedger: (slug: string) => void
  isLoading: boolean
}

const LedgerContext = createContext<LedgerContextType | undefined>(undefined)

/**
 * Resolves the active ledger once for all pages. Resolution order:
 * URL :ledgerSlug param > last selection (localStorage) > first ledger.
 */
export function LedgerProvider({ children }: { children: ReactNode }) {
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { data, isLoading } = useLedgers()
  const [selectedSlug, setSelectedSlug] = useState<string | null>(
    () => ledgerSlug ?? localStorage.getItem(STORAGE_KEY),
  )

  // A ledger-scoped URL always wins and updates the remembered selection.
  useEffect(() => {
    if (ledgerSlug) {
      setSelectedSlug(ledgerSlug)
      localStorage.setItem(STORAGE_KEY, ledgerSlug)
    }
  }, [ledgerSlug])

  const ledgers = data?.data ?? []
  const currentLedger =
    ledgers.find((l) => l.attributes.slug === selectedSlug) ?? ledgers[0] ?? null

  const setCurrentLedger = useCallback((slug: string) => {
    setSelectedSlug(slug)
    localStorage.setItem(STORAGE_KEY, slug)
  }, [])

  return (
    <LedgerContext.Provider value={{ ledgers, currentLedger, setCurrentLedger, isLoading }}>
      {children}
    </LedgerContext.Provider>
  )
}

export function useLedger(): LedgerContextType {
  const context = useContext(LedgerContext)
  if (context === undefined) {
    throw new Error("useLedger must be used within a LedgerProvider")
  }
  return context
}
