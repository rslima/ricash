import { useState } from "react"
import type { AccountResource, TransactionResource } from "@/api/types"
import {
  type TransactionEntry,
  autoBalanceEntries,
  calculateTotal,
  isBalanced,
  isFormValid,
} from "@/lib/transaction-entries"
import { todayISO } from "@/lib/dates"

interface UseTransactionFormOptions {
  /** Ledger currency: seeds new entries and is the display currency for totals. */
  defaultCurrency: string
  accounts: AccountResource[]
  /** accountId → envelopeId auto-fill mapping. */
  envelopeMappings: Record<string, string>
}

/**
 * Owns one transaction form instance (date, description, entries) plus the
 * entry-editing behaviors the Transactions page previously duplicated across
 * its create and edit branches: toCurrency sync with the selected account's
 * currency, envelope auto-fill from account mappings, and blur-time
 * auto-balancing. Derived validity/totals come from @/lib/transaction-entries.
 */
export function useTransactionForm({
  defaultCurrency,
  accounts,
  envelopeMappings,
}: UseTransactionFormOptions) {
  const seedEntries = (): TransactionEntry[] => [
    { accountId: "", amount: "", currency: defaultCurrency, type: "CREDIT" },
    { accountId: "", amount: "", currency: defaultCurrency, type: "DEBIT" },
  ]

  const [date, setDate] = useState(todayISO())
  const [description, setDescription] = useState("")
  const [entries, setEntries] = useState<readonly TransactionEntry[]>(seedEntries)

  const addEntry = (type: "DEBIT" | "CREDIT") => {
    setEntries([...entries, { accountId: "", amount: "", currency: defaultCurrency, type }])
  }

  const removeEntry = (index: number) => {
    if (entries.length > 2) {
      setEntries(entries.filter((_, i) => i !== index))
    }
  }

  const updateEntry = (index: number, field: keyof TransactionEntry, value: string) => {
    const current = entries[index]
    if (!current) return

    const updated: TransactionEntry = { ...current, [field]: value }

    // Auto-fill toCurrency when account is selected and needs conversion
    if (field === "accountId" && value) {
      const account = accounts.find((a) => a.id === value)
      if (account && account.attributes.currency !== updated.currency) {
        updated.toCurrency = account.attributes.currency
      } else {
        // Clear conversion fields if same currency
        updated.toAmount = undefined
        updated.toCurrency = undefined
      }
      // Auto-fill envelope from account mapping
      const mappedEnvelope = envelopeMappings[value]
      if (!updated.envelopeId && mappedEnvelope) {
        updated.envelopeId = mappedEnvelope
      }
    }

    // Auto-update toCurrency when currency changes
    if (field === "currency" && updated.accountId) {
      const account = accounts.find((a) => a.id === updated.accountId)
      if (account && account.attributes.currency !== value) {
        updated.toCurrency = account.attributes.currency
      } else {
        // Clear conversion fields if same currency
        updated.toAmount = undefined
        updated.toCurrency = undefined
      }
    }

    setEntries(entries.map((entry, i) => (i === index ? updated : entry)))
  }

  // Trigger auto-balance when the user finishes editing an amount field.
  // autoBalanceEntries returns the same reference when nothing was filled.
  const handleAmountBlur = () => {
    const next = autoBalanceEntries(entries)
    if (next !== entries) {
      setEntries(next)
    }
  }

  /** Seed date, description, and entries from an existing transaction (edit flow). */
  const loadFromTransaction = (tx: TransactionResource) => {
    setDate(tx.attributes.date)
    setDescription(tx.attributes.description)

    const transactionEntries: TransactionEntry[] = tx.attributes.entries?.map((entry) => ({
      accountId: entry.accountId || "",
      amount: entry.amount?.toString() || "0",
      currency: entry.currency || defaultCurrency,
      toAmount: entry.toAmount?.toString(),
      toCurrency: entry.toCurrency,
      type: entry.type || "DEBIT",
      instrumentId: entry.instrumentId,
      quantity: entry.quantity?.toString(),
      envelopeId: entry.envelopeId,
    })) || []

    setEntries(transactionEntries.length > 0 ? transactionEntries : seedEntries())
  }

  /** Replace entries from a template; date and description are left untouched. */
  const loadFromTemplate = (template: TransactionResource) => {
    const templateEntries: TransactionEntry[] = template.attributes.entries?.map((entry) => ({
      accountId: entry.accountId || "",
      amount: entry.amount?.toString() || "",
      currency: entry.currency || defaultCurrency,
      toAmount: entry.toAmount?.toString(),
      toCurrency: entry.toCurrency,
      type: entry.type || "DEBIT",
      instrumentId: entry.instrumentId,
      quantity: entry.quantity?.toString(),
      envelopeId: entry.envelopeId,
    })) || []

    if (templateEntries.length > 0) {
      setEntries(templateEntries)
    }
  }

  const reset = () => {
    setDate(todayISO())
    setDescription("")
    setEntries(seedEntries())
  }

  return {
    date,
    setDate,
    description,
    setDescription,
    entries,
    setEntries,
    updateEntry,
    addEntry,
    removeEntry,
    handleAmountBlur,
    loadFromTransaction,
    loadFromTemplate,
    reset,
    ledgerCurrency: defaultCurrency,
    isValid: isFormValid(entries, date, description),
    debitTotal: calculateTotal("DEBIT", entries),
    creditTotal: calculateTotal("CREDIT", entries),
    balanced: isBalanced(entries),
  }
}
