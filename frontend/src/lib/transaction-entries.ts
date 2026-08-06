/**
 * Pure form/domain logic for double-entry transaction entries, extracted from
 * the Transactions page so it can be unit-tested. `isBalanced` must match the
 * backend rule in TransactionServiceBean.validateMultiCurrencyBalance: group
 * by ORIGINAL currency (entry.currency) using ORIGINAL amounts (entry.amount),
 * so multi-currency transactions balance per transaction currency.
 */
export interface TransactionEntry {
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

/** Safe number parser to avoid NaN propagation from free-text inputs. */
export function parseAmount(value: string | undefined): number {
  if (!value || value.trim() === "") return 0
  const parsed = parseFloat(value)
  return isNaN(parsed) ? 0 : parsed
}

/** Whether an amount counts as "empty" for auto-balance purposes. */
export function isAmountEmpty(amount: string | undefined): boolean {
  if (!amount || amount.trim() === "") return true
  const parsed = parseFloat(amount)
  return isNaN(parsed) || parsed === 0
}

export function calculateTotal(type: "DEBIT" | "CREDIT", entries: readonly TransactionEntry[]): number {
  return entries
    .filter((e) => e.type === type)
    .reduce((sum, e) => sum + (parseFloat(e.amount) || 0), 0)
}

/**
 * Auto-balance: per currency, if exactly one entry has an empty/zero amount
 * and the amount needed to balance is positive, fill it in. Returns the input
 * array unchanged (same reference) when nothing was filled.
 */
export function autoBalanceEntries(entries: readonly TransactionEntry[]): readonly TransactionEntry[] {
  const byCurrency: Record<string, { entries: { index: number; entry: TransactionEntry }[]; debits: number; credits: number }> = {}

  entries.forEach((entry, index) => {
    const currency = entry.currency
    if (!currency) return

    if (!byCurrency[currency]) {
      byCurrency[currency] = { entries: [], debits: 0, credits: 0 }
    }

    byCurrency[currency].entries.push({ index, entry })

    // Only non-empty amounts count toward the balance calculation.
    if (!isAmountEmpty(entry.amount)) {
      const amount = parseAmount(entry.amount)
      if (entry.type === "DEBIT") {
        byCurrency[currency].debits += amount
      } else {
        byCurrency[currency].credits += amount
      }
    }
  })

  const newEntries = [...entries]
  let modified = false

  for (const currency of Object.keys(byCurrency)) {
    const group = byCurrency[currency]
    if (!group) continue

    const emptyEntries = group.entries.filter(({ entry }) => isAmountEmpty(entry.amount))
    const empty = emptyEntries.length === 1 ? emptyEntries[0] : undefined
    if (!empty) continue

    const { debits, credits } = group
    const neededAmount = empty.entry.type === "DEBIT" ? credits - debits : debits - credits

    if (neededAmount > 0) {
      newEntries[empty.index] = { ...empty.entry, amount: neededAmount.toFixed(2) }
      modified = true
    }
  }

  return modified ? newEntries : entries
}

/** Resolves an account id to its currency; unknown/unselected accounts give undefined. */
export type AccountCurrencyLookup = (accountId: string) => string | undefined

/**
 * The overwhelmingly common transaction shape is one or more debits against a
 * single credit in one currency, so that lone credit amount is never typed: it
 * is derived from the debits it funds. Anything with a rate or a price in it —
 * several credits, an instrument leg (buy/sell), or more than one currency
 * across the entries and their accounts (a conversion) — falls back to manual
 * entry, since the two sides of those are not the same number.
 */
export function hasDerivedCreditAmount(
  entries: readonly TransactionEntry[],
  accountCurrency: AccountCurrencyLookup
): boolean {
  if (entries.filter((e) => e.type === "CREDIT").length !== 1) return false
  if (entries.some((e) => e.instrumentId)) return false

  const currencies = new Set<string>()
  for (const entry of entries) {
    if (entry.currency) currencies.add(entry.currency)
    const accounted = accountCurrency(entry.accountId)
    if (accounted) currencies.add(accounted)
  }
  return currencies.size === 1
}

/**
 * Keep a lone credit entry's amount equal to the sum of the debits. Only runs
 * when `hasDerivedCreditAmount` holds, so every entry shares one currency and
 * the sum needs no conversion. Returns the input array unchanged (same
 * reference) when the amount is already in sync.
 */
export function syncDerivedCreditAmount(
  entries: readonly TransactionEntry[],
  accountCurrency: AccountCurrencyLookup
): readonly TransactionEntry[] {
  if (!hasDerivedCreditAmount(entries, accountCurrency)) return entries

  const index = entries.findIndex((e) => e.type === "CREDIT")
  const credit = entries[index]
  if (!credit) return entries

  const debitTotal = entries
    .filter((e) => e.type === "DEBIT")
    .reduce((sum, e) => sum + parseAmount(e.amount), 0)

  const amount = debitTotal > 0 ? debitTotal.toFixed(2) : ""
  if (credit.amount === amount) return entries

  const next = [...entries]
  next[index] = { ...credit, amount }
  return next
}

/** Every currency group must have debits ≈ credits (0.01 tolerance). */
export function isBalanced(entries: readonly TransactionEntry[]): boolean {
  if (!entries || entries.length === 0) return false

  const byCurrency: Record<string, { debits: number; credits: number }> = {}

  for (const entry of entries) {
    const currency = entry.currency
    if (!currency) continue

    if (!byCurrency[currency]) {
      byCurrency[currency] = { debits: 0, credits: 0 }
    }

    const amount = parseAmount(entry.amount)
    if (entry.type === "DEBIT") {
      byCurrency[currency].debits += amount
    } else {
      byCurrency[currency].credits += amount
    }
  }

  const currencies = Object.keys(byCurrency)
  if (currencies.length === 0) return false

  return currencies.every((currency) => {
    const group = byCurrency[currency]
    if (!group) return true
    return Math.abs(group.debits - group.credits) < 0.01
  })
}

/** Date + description present, every entry complete and positive, and balanced. */
export function isFormValid(
  entries: readonly TransactionEntry[],
  dateValue: string,
  descValue: string
): boolean {
  if (!dateValue || !descValue.trim()) return false

  const entriesValid = entries.every((e) => {
    if (!e.accountId || !e.amount || parseFloat(e.amount) <= 0) return false
    // toAmount is optional — if provided, it must not be negative.
    if (e.toAmount && parseFloat(e.toAmount) < 0) return false
    return true
  })
  if (!entriesValid) return false

  return isBalanced(entries)
}
