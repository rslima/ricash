import { describe, it, expect } from "vitest"
import {
  type TransactionEntry,
  parseAmount,
  isAmountEmpty,
  calculateTotal,
  autoBalanceEntries,
  isBalanced,
  isFormValid,
} from "./transaction-entries"

function entry(overrides: Partial<TransactionEntry> = {}): TransactionEntry {
  return {
    accountId: "acc-1",
    amount: "100",
    currency: "BRL",
    type: "DEBIT",
    ...overrides,
  }
}

describe("parseAmount", () => {
  it("parses numeric strings", () => {
    expect(parseAmount("12.5")).toBe(12.5)
  })

  it("returns 0 for empty, whitespace, undefined, and non-numeric input", () => {
    expect(parseAmount("")).toBe(0)
    expect(parseAmount("   ")).toBe(0)
    expect(parseAmount(undefined)).toBe(0)
    expect(parseAmount("abc")).toBe(0)
  })
})

describe("isAmountEmpty", () => {
  it("treats empty, zero, and invalid values as empty", () => {
    expect(isAmountEmpty("")).toBe(true)
    expect(isAmountEmpty(undefined)).toBe(true)
    expect(isAmountEmpty("0")).toBe(true)
    expect(isAmountEmpty("abc")).toBe(true)
  })

  it("treats non-zero numbers as filled", () => {
    expect(isAmountEmpty("0.01")).toBe(false)
    expect(isAmountEmpty("-5")).toBe(false)
  })
})

describe("calculateTotal", () => {
  it("sums only entries of the requested type", () => {
    const entries = [
      entry({ type: "DEBIT", amount: "10" }),
      entry({ type: "DEBIT", amount: "5.5" }),
      entry({ type: "CREDIT", amount: "20" }),
    ]
    expect(calculateTotal("DEBIT", entries)).toBe(15.5)
    expect(calculateTotal("CREDIT", entries)).toBe(20)
  })
})

describe("autoBalanceEntries", () => {
  it("fills the single empty entry with the amount needed to balance", () => {
    const entries = [
      entry({ type: "CREDIT", amount: "150" }),
      entry({ type: "DEBIT", amount: "" }),
    ]
    const balanced = autoBalanceEntries(entries)
    expect(balanced[1].amount).toBe("150.00")
  })

  it("balances per currency independently", () => {
    const entries = [
      entry({ type: "CREDIT", amount: "100", currency: "BRL" }),
      entry({ type: "DEBIT", amount: "", currency: "BRL" }),
      entry({ type: "CREDIT", amount: "30", currency: "USD" }),
      entry({ type: "DEBIT", amount: "", currency: "USD" }),
    ]
    const balanced = autoBalanceEntries(entries)
    expect(balanced[1].amount).toBe("100.00")
    expect(balanced[3].amount).toBe("30.00")
  })

  it("does nothing when more than one entry is empty in a currency", () => {
    const entries = [
      entry({ type: "CREDIT", amount: "100" }),
      entry({ type: "DEBIT", amount: "" }),
      entry({ type: "DEBIT", amount: "0" }),
    ]
    expect(autoBalanceEntries(entries)).toBe(entries)
  })

  it("does nothing when the needed amount is not positive", () => {
    const entries = [
      entry({ type: "DEBIT", amount: "100" }),
      entry({ type: "DEBIT", amount: "" }),
    ]
    expect(autoBalanceEntries(entries)).toBe(entries)
  })

  it("returns the same reference when nothing changed", () => {
    const entries = [
      entry({ type: "CREDIT", amount: "50" }),
      entry({ type: "DEBIT", amount: "50" }),
    ]
    expect(autoBalanceEntries(entries)).toBe(entries)
  })
})

describe("isBalanced", () => {
  it("accepts equal debits and credits in one currency", () => {
    expect(
      isBalanced([entry({ type: "DEBIT", amount: "100" }), entry({ type: "CREDIT", amount: "100" })])
    ).toBe(true)
  })

  it("rejects unbalanced entries", () => {
    expect(
      isBalanced([entry({ type: "DEBIT", amount: "100" }), entry({ type: "CREDIT", amount: "90" })])
    ).toBe(false)
  })

  it("groups by ORIGINAL currency, matching the backend rule", () => {
    // A BRL→USD conversion balances per original currency even though the
    // account-side (converted) amounts differ.
    expect(
      isBalanced([
        entry({ type: "CREDIT", amount: "500", currency: "BRL" }),
        entry({ type: "DEBIT", amount: "500", currency: "BRL", toAmount: "100", toCurrency: "USD" }),
      ])
    ).toBe(true)
    expect(
      isBalanced([
        entry({ type: "CREDIT", amount: "500", currency: "BRL" }),
        entry({ type: "DEBIT", amount: "100", currency: "USD" }),
      ])
    ).toBe(false)
  })

  it("tolerates sub-cent rounding differences", () => {
    expect(
      isBalanced([
        entry({ type: "DEBIT", amount: "33.333" }),
        entry({ type: "CREDIT", amount: "33.34" }),
      ])
    ).toBe(true)
  })

  it("rejects empty lists and entries without a currency", () => {
    expect(isBalanced([])).toBe(false)
    expect(isBalanced([entry({ currency: "" })])).toBe(false)
  })
})

describe("isFormValid", () => {
  const balanced = [
    entry({ type: "DEBIT", amount: "100" }),
    entry({ type: "CREDIT", amount: "100" }),
  ]

  it("accepts a complete balanced form", () => {
    expect(isFormValid(balanced, "2026-01-15", "Groceries")).toBe(true)
  })

  it("requires date and non-blank description", () => {
    expect(isFormValid(balanced, "", "Groceries")).toBe(false)
    expect(isFormValid(balanced, "2026-01-15", "   ")).toBe(false)
  })

  it("requires every entry to have an account and a positive amount", () => {
    expect(isFormValid([entry({ accountId: "" }), entry({ type: "CREDIT" })], "2026-01-15", "x")).toBe(false)
    expect(isFormValid([entry({ amount: "0" }), entry({ type: "CREDIT", amount: "0" })], "2026-01-15", "x")).toBe(false)
  })

  it("rejects negative conversion amounts", () => {
    const entries = [
      entry({ type: "DEBIT", amount: "100", toAmount: "-1", toCurrency: "USD" }),
      entry({ type: "CREDIT", amount: "100" }),
    ]
    expect(isFormValid(entries, "2026-01-15", "x")).toBe(false)
  })

  it("requires the entries to balance", () => {
    const entries = [entry({ type: "DEBIT", amount: "100" }), entry({ type: "CREDIT", amount: "90" })]
    expect(isFormValid(entries, "2026-01-15", "x")).toBe(false)
  })
})
