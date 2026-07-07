import { useCallback, useState } from "react"
import type { TransactionEntryInput } from "@/api/transactions"
import type { AccountResource, TransactionResource } from "@/api/types"

export interface TransactionEntryFormValue {
  accountId: string
  amount: string
  currency: string
  toAmount?: string
  toCurrency?: string
  type: "DEBIT" | "CREDIT"
  instrumentId?: string
  quantity?: string
  envelopeId?: string
}

export function emptyEntryPair(defaultCurrency: string): TransactionEntryFormValue[] {
  return [
    { accountId: "", amount: "", currency: defaultCurrency, type: "CREDIT" },
    { accountId: "", amount: "", currency: defaultCurrency, type: "DEBIT" },
  ]
}

/** Converts a transaction resource's entries into editable form values. */
export function entriesFromTransaction(
  transaction: TransactionResource,
  defaultCurrency: string,
): TransactionEntryFormValue[] {
  return (
    transaction.attributes.entries?.map((entry) => ({
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
  )
}

/** Converts form values into the API's entry input shape. */
export function toEntryInputs(entries: TransactionEntryFormValue[]): TransactionEntryInput[] {
  return entries.map((e) => ({
    accountId: e.accountId,
    amount: parseFloat(e.amount),
    currency: e.currency,
    toAmount: e.toAmount ? parseFloat(e.toAmount) : undefined,
    toCurrency: e.toCurrency || undefined,
    type: e.type,
    instrumentId: e.instrumentId || undefined,
    quantity: e.quantity ? parseFloat(e.quantity) : undefined,
    envelopeId: e.envelopeId || undefined,
  }))
}

// Safe number parser to avoid NaN propagation
function parseAmount(value: string | undefined): number {
  if (!value || value.trim() === "") return 0
  const parsed = parseFloat(value)
  return isNaN(parsed) ? 0 : parsed
}

// An amount is "empty" for auto-balance purposes when blank, invalid or zero
function isAmountEmpty(amount: string | undefined): boolean {
  if (!amount || amount.trim() === "") return true
  const parsed = parseFloat(amount)
  return isNaN(parsed) || parsed === 0
}

export function calculateTotal(type: "DEBIT" | "CREDIT", entries: TransactionEntryFormValue[]): number {
  return entries
    .filter((e) => e.type === type)
    .reduce((sum, e) => sum + parseAmount(e.amount), 0)
}

/**
 * Multi-currency balance validation. Must match the backend logic in
 * TransactionServiceBean.validateMultiCurrencyBalance(): group by the
 * ORIGINAL currency (entry.currency) and use the ORIGINAL amount, so
 * multi-currency transactions balance in the transaction currency.
 */
export function isBalanced(entries: TransactionEntryFormValue[]): boolean {
  if (!entries || entries.length === 0) return false

  const byCurrency: Record<string, { debits: number; credits: number }> = {}
  for (const entry of entries) {
    if (!entry.currency) continue
    byCurrency[entry.currency] ??= { debits: 0, credits: 0 }
    const amount = parseAmount(entry.amount)
    if (entry.type === "DEBIT") {
      byCurrency[entry.currency].debits += amount
    } else {
      byCurrency[entry.currency].credits += amount
    }
  }

  const currencies = Object.keys(byCurrency)
  if (currencies.length === 0) return false

  return currencies.every((currency) => {
    const { debits, credits } = byCurrency[currency]
    return Math.abs(debits - credits) < 0.01
  })
}

export function isTransactionFormValid(
  entries: TransactionEntryFormValue[],
  date: string,
  description: string,
): boolean {
  if (!date || !description.trim()) return false

  const entriesValid = entries.every((e) => {
    if (!e.accountId || !e.amount || parseFloat(e.amount) <= 0) return false
    // toAmount is optional - if provided, it must not be negative
    if (e.toAmount && parseFloat(e.toAmount) < 0) return false
    return true
  })
  if (!entriesValid) return false

  return isBalanced(entries)
}

/**
 * If exactly one entry of a currency group has an empty/zero amount, fill it
 * with the value needed to balance that group.
 */
export function autoBalanceEntries(entries: TransactionEntryFormValue[]): TransactionEntryFormValue[] {
  const byCurrency: Record<
    string,
    { entries: { index: number; entry: TransactionEntryFormValue }[]; debits: number; credits: number }
  > = {}

  entries.forEach((entry, index) => {
    if (!entry.currency) return
    byCurrency[entry.currency] ??= { entries: [], debits: 0, credits: 0 }
    byCurrency[entry.currency].entries.push({ index, entry })
    if (!isAmountEmpty(entry.amount)) {
      const amount = parseAmount(entry.amount)
      if (entry.type === "DEBIT") {
        byCurrency[entry.currency].debits += amount
      } else {
        byCurrency[entry.currency].credits += amount
      }
    }
  })

  const newEntries = [...entries]
  let modified = false

  for (const group of Object.values(byCurrency)) {
    const emptyEntries = group.entries.filter(({ entry }) => isAmountEmpty(entry.amount))
    if (emptyEntries.length !== 1) continue

    const { index: emptyIndex, entry: emptyEntry } = emptyEntries[0]
    const neededAmount =
      emptyEntry.type === "DEBIT" ? group.credits - group.debits : group.debits - group.credits

    if (neededAmount > 0) {
      newEntries[emptyIndex] = { ...newEntries[emptyIndex], amount: neededAmount.toFixed(2) }
      modified = true
    }
  }

  return modified ? newEntries : entries
}

interface UseTransactionEntriesOptions {
  accounts: AccountResource[]
  envelopeMappings: Record<string, string>
  defaultCurrency: string
}

/**
 * Owns the entry list of one transaction dialog: add/remove rows, field
 * updates with account-currency/envelope auto-fill, and blur auto-balance.
 */
export function useTransactionEntries({ accounts, envelopeMappings, defaultCurrency }: UseTransactionEntriesOptions) {
  const [entries, setEntries] = useState<TransactionEntryFormValue[]>(() => emptyEntryPair(defaultCurrency))

  const addEntry = (type: "DEBIT" | "CREDIT") => {
    setEntries((prev) => [...prev, { accountId: "", amount: "", currency: defaultCurrency, type }])
  }

  const removeEntry = (index: number) => {
    setEntries((prev) => (prev.length > 2 ? prev.filter((_, i) => i !== index) : prev))
  }

  const updateEntry = (index: number, field: keyof TransactionEntryFormValue, value: string) => {
    setEntries((prev) => {
      const next = [...prev]
      next[index] = { ...next[index], [field]: value }

      const syncConversion = (accountId: string, entryCurrency: string) => {
        const account = accounts.find((a) => a.id === accountId)
        if (account && account.attributes.currency !== entryCurrency) {
          next[index].toCurrency = account.attributes.currency
        } else {
          next[index].toAmount = undefined
          next[index].toCurrency = undefined
        }
      }

      if (field === "accountId" && value) {
        syncConversion(value, next[index].currency)
        // Auto-fill envelope from account mapping
        if (!next[index].envelopeId && envelopeMappings[value]) {
          next[index].envelopeId = envelopeMappings[value]
        }
      }

      if (field === "currency" && next[index].accountId) {
        syncConversion(next[index].accountId, value)
      }

      return next
    })
  }

  const handleAmountBlur = () => {
    setEntries((prev) => autoBalanceEntries(prev))
  }

  const reset = useCallback(
    (initial?: TransactionEntryFormValue[]) => {
      setEntries(initial && initial.length > 0 ? initial : emptyEntryPair(defaultCurrency))
    },
    [defaultCurrency],
  )

  return { entries, addEntry, removeEntry, updateEntry, handleAmountBlur, reset }
}
