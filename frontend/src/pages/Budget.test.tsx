import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Budget } from "./Budget"
import * as envelopesApi from "@/api/envelopes"
import * as ledgersApi from "@/api/ledgers"
import type { EnvelopeBalance, BudgetSummary } from "@/api/envelopes"
import type { EnvelopeResource } from "@/api/types"
import { renderWithProviders, mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"
import { makeLedger } from "@/test/fixtures"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the APIs (the page uses envelopes hooks; ledgers come in through
// useLedgerSelection; accounts is a transitive dep of envelopes.hooks)
vi.mock("@/api/envelopes", () => ({
  getEnvelopes: vi.fn(),
  getEnvelope: vi.fn(),
  getEnvelopeBalance: vi.fn(),
  getEnvelopeAccounts: vi.fn(),
  getBudgetSummary: vi.fn(),
  getEnvelopeMappings: vi.fn(),
  createEnvelope: vi.fn(),
  updateEnvelope: vi.fn(),
  deleteEnvelope: vi.fn(),
  allocateEnvelope: vi.fn(),
  setEnvelopeAccounts: vi.fn(),
}))

vi.mock("@/api/accounts", () => ({
  getAccounts: vi.fn(),
  getAccount: vi.fn(),
  getBalanceSummary: vi.fn(),
  createAccount: vi.fn(),
  updateAccount: vi.fn(),
  deleteAccount: vi.fn(),
}))

vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
  createLedger: vi.fn(),
  updateLedger: vi.fn(),
  deleteLedger: vi.fn(),
}))

import { useAuth } from "@/contexts/AuthContext"

// Local envelope factory (fixtures.ts has no makeEnvelope); same two-level
// merge shape as the shared factories.
type EnvelopeOverrides = Omit<Partial<EnvelopeResource>, "attributes"> & {
  attributes?: Partial<EnvelopeResource["attributes"]>
}

function makeEnvelope(overrides?: EnvelopeOverrides): EnvelopeResource {
  const base: EnvelopeResource = {
    type: "envelopes",
    id: "envelope-1",
    attributes: {
      name: "Groceries",
      description: null,
      currency: "USD",
      type: "EXPENSE",
      status: "ACTIVE",
      parentEnvelopeId: null,
      createdAt: "2024-01-01T00:00:00Z",
    },
  }
  return { ...base, ...overrides, attributes: { ...base.attributes, ...overrides?.attributes } }
}

// The page defaults to the current month, so derive the expected period the
// same way it does.
const now = new Date()
const currentYear = now.getFullYear()
const currentMonth = now.getMonth() + 1

function makeBalance(envelopeId: string, overrides?: Partial<EnvelopeBalance>): EnvelopeBalance {
  return {
    envelopeId,
    periodYear: currentYear,
    periodMonth: currentMonth,
    rollover: 0,
    allocated: 0,
    spent: 0,
    available: 0,
    ...overrides,
  }
}

function makeBudgetSummary(overrides?: Partial<BudgetSummary>): BudgetSummary {
  return {
    id: "budget-1",
    periodYear: currentYear,
    periodMonth: currentMonth,
    toBeBudgeted: 0,
    envelopeBalances: [],
    ...overrides,
  }
}

const mockLedger = makeLedger()

function mockLedgerWith(envelopes: EnvelopeResource[], summary: BudgetSummary = makeBudgetSummary()) {
  vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
  vi.mocked(envelopesApi.getEnvelopes).mockResolvedValue({ data: envelopes })
  vi.mocked(envelopesApi.getBudgetSummary).mockResolvedValue(summary)
}

const renderBudget = () => renderWithProviders(<Budget />)

describe("Budget", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderBudget()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your budget")).toBeInTheDocument()
    })
  })

  describe("when authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(
        mockUseAuth({ isAuthenticated: true, user: mockAuthenticatedUser, accessToken: "test-token" })
      )
    })

    it("shows page title and no ledger selected when there are no ledgers", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [] })

      renderBudget()

      expect(screen.getByRole("heading", { name: "Budget" })).toBeInTheDocument()
      expect(screen.getByText("Track your monthly budget allocation and spending")).toBeInTheDocument()

      await waitFor(() => {
        expect(screen.getByText("No ledger selected")).toBeInTheDocument()
      })
    })

    it("displays the amount to be budgeted for the current month", async () => {
      mockLedgerWith([makeEnvelope()], makeBudgetSummary({ toBeBudgeted: 2000 }))

      renderBudget()

      await waitFor(() => {
        // en locale (pinned in test setup) formats the ledger's USD currency
        expect(screen.getByText("$2,000.00")).toBeInTheDocument()
      })

      expect(screen.getByText("To Be Budgeted")).toBeInTheDocument()
      expect(screen.getByText("You have money available to assign to envelopes")).toBeInTheDocument()
      expect(envelopesApi.getBudgetSummary).toHaveBeenCalledWith("personal-finance", currentYear, currentMonth)
    })

    it("shows the overbudgeted warning when to be budgeted is negative", async () => {
      mockLedgerWith([makeEnvelope()], makeBudgetSummary({ toBeBudgeted: -50 }))

      renderBudget()

      await waitFor(() => {
        expect(screen.getByText("-$50.00")).toBeInTheDocument()
      })

      expect(screen.getByText("You've assigned more money than available")).toBeInTheDocument()
    })

    it("displays envelope rows with their balances", async () => {
      const envelopes = [
        makeEnvelope({ id: "env-groceries", attributes: { name: "Groceries", type: "EXPENSE" } }),
        makeEnvelope({ id: "env-salary", attributes: { name: "Salary", type: "INCOME" } }),
      ]
      const summary = makeBudgetSummary({
        toBeBudgeted: 1000,
        envelopeBalances: [
          makeBalance("env-groceries", { rollover: 100, allocated: 500, spent: 200, available: 400 }),
          makeBalance("env-salary", { allocated: 3000, spent: 2500, available: 800 }),
        ],
      })
      mockLedgerWith(envelopes, summary)

      renderBudget()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
        expect(screen.getByText("Salary")).toBeInTheDocument()
      })

      // Section cards per envelope type
      expect(screen.getByText("Expense Limits")).toBeInTheDocument()
      expect(screen.getByText("Income Targets")).toBeInTheDocument()
      // Expense row: rollover, allocated (click-to-edit button), spent, available
      expect(screen.getByText("$100.00")).toBeInTheDocument()
      expect(screen.getByRole("button", { name: "$500.00" })).toBeInTheDocument()
      expect(screen.getByText("$200.00")).toBeInTheDocument()
      expect(screen.getByText("$400.00")).toBeInTheDocument()
      // Progress bar footer: spent / allocated and remaining
      expect(screen.getByText("$200.00 / $500.00")).toBeInTheDocument()
      expect(screen.getByText("$400.00 left")).toBeInTheDocument()
      // Income row: allocated, received, available; no rollover shows a dash
      expect(screen.getByRole("button", { name: "$3,000.00" })).toBeInTheDocument()
      expect(screen.getByText("$2,500.00")).toBeInTheDocument()
      expect(screen.getByText("$800.00")).toBeInTheDocument()
      expect(screen.getByText("-")).toBeInTheDocument()
    })

    it("shows empty state when there are no envelopes", async () => {
      mockLedgerWith([])

      renderBudget()

      await waitFor(() => {
        expect(screen.getByText("No envelopes yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create envelopes first to start budgeting")).toBeInTheDocument()
      expect(screen.getByRole("button", { name: "Go to Envelopes" })).toBeInTheDocument()
    })

    it("allocates money to an envelope", async () => {
      const user = userEvent.setup()
      const envelope = makeEnvelope({ id: "env-groceries", attributes: { name: "Groceries" } })
      const summary = makeBudgetSummary({
        envelopeBalances: [makeBalance("env-groceries", { allocated: 500, spent: 200, available: 300 })],
      })
      mockLedgerWith([envelope], summary)
      vi.mocked(envelopesApi.allocateEnvelope).mockResolvedValueOnce({
        data: {
          type: "envelope-allocations",
          id: "alloc-1",
          attributes: {
            envelopeId: "env-groceries",
            periodYear: currentYear,
            periodMonth: currentMonth,
            allocatedAmount: 750,
            notes: null,
            createdAt: "2024-01-01T00:00:00Z",
            updatedAt: "2024-01-01T00:00:00Z",
          },
        },
      })

      renderBudget()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
      })

      // Click the allocated amount to edit it inline
      await user.click(screen.getByRole("button", { name: "$500.00" }))

      const input = screen.getByRole("spinbutton")
      await user.clear(input)
      await user.type(input, "750")
      await user.keyboard("{Enter}")

      await waitFor(() => {
        expect(envelopesApi.allocateEnvelope).toHaveBeenCalledWith("personal-finance", "env-groceries", {
          year: currentYear,
          month: currentMonth,
          allocatedAmount: 750,
        })
      })
    })

    it("navigates to the previous month", async () => {
      const user = userEvent.setup()
      mockLedgerWith([])

      renderBudget()

      await waitFor(() => {
        expect(envelopesApi.getBudgetSummary).toHaveBeenCalledWith("personal-finance", currentYear, currentMonth)
      })

      await user.click(screen.getByRole("button", { name: "Previous month" }))

      const previousMonth = currentMonth === 1 ? 12 : currentMonth - 1
      const previousYear = currentMonth === 1 ? currentYear - 1 : currentYear

      await waitFor(() => {
        expect(envelopesApi.getBudgetSummary).toHaveBeenCalledWith("personal-finance", previousYear, previousMonth)
      })
    })

    it("navigates to the next month", async () => {
      const user = userEvent.setup()
      mockLedgerWith([])

      renderBudget()

      await waitFor(() => {
        expect(envelopesApi.getBudgetSummary).toHaveBeenCalledWith("personal-finance", currentYear, currentMonth)
      })

      await user.click(screen.getByRole("button", { name: "Next month" }))

      const nextMonth = currentMonth === 12 ? 1 : currentMonth + 1
      const nextYear = currentMonth === 12 ? currentYear + 1 : currentYear

      await waitFor(() => {
        expect(envelopesApi.getBudgetSummary).toHaveBeenCalledWith("personal-finance", nextYear, nextMonth)
      })
    })
  })
})
