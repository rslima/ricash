import { Button } from "@/components/ui/button"
import type { LedgerResource } from "@/api/types"

interface LedgerSelectorProps {
  ledgers: LedgerResource[]
  selectedSlug: string | null
  /** Owns side effects beyond selection (e.g. resetting pagination). */
  onSelect: (slug: string) => void
}

/** The ledger tab-button row shown on every ledger-scoped page. */
export function LedgerSelector({ ledgers, selectedSlug, onSelect }: LedgerSelectorProps) {
  if (ledgers.length === 0) return null
  return (
    <div className="flex gap-2 flex-wrap">
      {ledgers.map((ledger) => (
        <Button
          key={ledger.id}
          variant={selectedSlug === ledger.attributes.slug ? "default" : "outline"}
          size="sm"
          onClick={() => onSelect(ledger.attributes.slug)}
        >
          {ledger.attributes.name}
        </Button>
      ))}
    </div>
  )
}
