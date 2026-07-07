import { describe, it, expect } from "vitest"
import { renderHook, act } from "@testing-library/react"
import {
  useTransactionEntries,
  autoBalanceEntries,
  isBalanced,
  isTransactionFormValid,
  type TransactionEntryFormValue,
} from "./useTransactionEntries"
import type { AccountResource } from "@/api/types"

function account(id: string, currency: string): AccountResource {
  return {
    type: "accounts",
    id,
    attributes: {
      slug: id,
      name: id,
      type: "ASSET",
      currency,
      balance: 0,
      description: null,
      parentAccountId: null,
      createdAt: "2024-01-01T00:00:00Z",
      updatedAt: "2024-01-01T00:00:00Z",
    },
  } as unknown as AccountResource
}

const ACCOUNTS = [account("usd-account", "USD"), account("brl-account", "BRL")]

function setup(envelopeMappings: Record<string, string> = {}) {
  return renderHook(() =>
    useTransactionEntries({ accounts: ACCOUNTS, envelopeMappings, defaultCurrency: "USD" })
  )
}

describe("useTransactionEntries", () => {
  it("starts with one credit and one debit entry", () => {
    const { result } = setup()

    expect(result.current.entries).toHaveLength(2)
    expect(result.current.entries[0].type).toBe("CREDIT")
    expect(result.current.entries[1].type).toBe("DEBIT")
    expect(result.current.entries[0].currency).toBe("USD")
  })

  it("adds and removes entries but never drops below two", () => {
    const { result } = setup()

    act(() => result.current.addEntry("DEBIT"))
    expect(result.current.entries).toHaveLength(3)

    act(() => result.current.removeEntry(2))
    expect(result.current.entries).toHaveLength(2)

    act(() => result.current.removeEntry(1))
    expect(result.current.entries).toHaveLength(2)
  })

  it("auto-fills conversion currency when the account currency differs", () => {
    const { result } = setup()

    act(() => result.current.updateEntry(0, "accountId", "brl-account"))

    expect(result.current.entries[0].toCurrency).toBe("BRL")
  })

  it("clears conversion fields when currencies match again", () => {
    const { result } = setup()

    act(() => result.current.updateEntry(0, "accountId", "brl-account"))
    act(() => result.current.updateEntry(0, "currency", "BRL"))

    expect(result.current.entries[0].toCurrency).toBeUndefined()
    expect(result.current.entries[0].toAmount).toBeUndefined()
  })

  it("auto-fills the envelope from the account mapping", () => {
    const { result } = setup({ "usd-account": "envelope-1" })

    act(() => result.current.updateEntry(0, "accountId", "usd-account"))

    expect(result.current.entries[0].envelopeId).toBe("envelope-1")
  })

  it("auto-balances the single empty entry on blur", () => {
    const { result } = setup()

    act(() => result.current.updateEntry(0, "amount", "150.00"))
    act(() => result.current.handleAmountBlur())

    expect(result.current.entries[1].amount).toBe("150.00")
  })

  it("does not auto-balance when more than one entry is empty", () => {
    const { result } = setup()

    act(() => result.current.addEntry("DEBIT"))
    act(() => result.current.updateEntry(0, "amount", "150.00"))
    act(() => result.current.handleAmountBlur())

    expect(result.current.entries[1].amount).toBe("")
    expect(result.current.entries[2].amount).toBe("")
  })
})

describe("autoBalanceEntries", () => {
  it("balances per currency group", () => {
    const entries: TransactionEntryFormValue[] = [
      { accountId: "a", amount: "100", currency: "USD", type: "CREDIT" },
      { accountId: "b", amount: "", currency: "USD", type: "DEBIT" },
      { accountId: "c", amount: "50", currency: "BRL", type: "CREDIT" },
      { accountId: "d", amount: "50", currency: "BRL", type: "DEBIT" },
    ]

    const balanced = autoBalanceEntries(entries)

    expect(balanced[1].amount).toBe("100.00")
    expect(balanced[3].amount).toBe("50")
  })
})

describe("isBalanced / isTransactionFormValid", () => {
  const balancedEntries: TransactionEntryFormValue[] = [
    { accountId: "a", amount: "100", currency: "USD", type: "CREDIT" },
    { accountId: "b", amount: "100", currency: "USD", type: "DEBIT" },
  ]

  it("accepts balanced multi-currency entries grouped by original currency", () => {
    const entries: TransactionEntryFormValue[] = [
      { accountId: "a", amount: "1067.93", currency: "BRL", type: "CREDIT" },
      { accountId: "b", amount: "1067.93", currency: "BRL", toAmount: "191.88", toCurrency: "USD", type: "DEBIT" },
    ]
    expect(isBalanced(entries)).toBe(true)
  })

  it("rejects unbalanced entries", () => {
    expect(isBalanced([
      { accountId: "a", amount: "100", currency: "USD", type: "CREDIT" },
      { accountId: "b", amount: "90", currency: "USD", type: "DEBIT" },
    ])).toBe(false)
  })

  it("requires date, description, accounts and positive amounts", () => {
    expect(isTransactionFormValid(balancedEntries, "2026-01-01", "Groceries")).toBe(true)
    expect(isTransactionFormValid(balancedEntries, "", "Groceries")).toBe(false)
    expect(isTransactionFormValid(balancedEntries, "2026-01-01", "  ")).toBe(false)
    expect(isTransactionFormValid(
      [{ accountId: "", amount: "100", currency: "USD", type: "CREDIT" }, ...balancedEntries.slice(1)],
      "2026-01-01",
      "Groceries",
    )).toBe(false)
  })
})
