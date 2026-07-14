import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import { Dashboard } from "./Dashboard"
import * as ledgersApi from "@/api/ledgers"
import * as accountsApi from "@/api/accounts"
import * as transactionsApi from "@/api/transactions"
import * as envelopesApi from "@/api/envelopes"
import * as instrumentsApi from "@/api/instruments"
import type { BalanceSummary } from "@/api/accounts"
import type { MonthlyReport } from "@/api/transactions"
import type { TransactionResource } from "@/api/types"
import { renderWithProviders, mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"
import { makeLedger } from "@/test/fixtures"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock every API module in the dashboard's import graph (page + chart children
// + the hooks modules they pull in).
vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
  createLedger: vi.fn(),
  updateLedger: vi.fn(),
  deleteLedger: vi.fn(),
}))

vi.mock("@/api/accounts", () => ({
  getAccounts: vi.fn(),
  getAccount: vi.fn(),
  getBalanceSummary: vi.fn(),
  createAccount: vi.fn(),
  updateAccount: vi.fn(),
  deleteAccount: vi.fn(),
}))

vi.mock("@/api/transactions", () => ({
  getTransactions: vi.fn(),
  getCategoryTransactions: vi.fn(),
  getMonthlyReport: vi.fn(),
  getMonthlyExpenseBreakdown: vi.fn(),
  getMonthlyIncomeBreakdown: vi.fn(),
  getTransactionDescriptions: vi.fn(),
  getTransactionTemplates: vi.fn(),
  createTransaction: vi.fn(),
  updateTransaction: vi.fn(),
  deleteTransaction: vi.fn(),
}))

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

vi.mock("@/api/instruments", () => ({
  getInstruments: vi.fn(),
  getAllInstruments: vi.fn(),
  getInstrument: vi.fn(),
  getInstrumentPrices: vi.fn(),
  getPortfolio: vi.fn(),
  getAccountPortfolio: vi.fn(),
  createInstrument: vi.fn(),
  updateInstrument: vi.fn(),
  deleteInstrument: vi.fn(),
  createInstrumentPrice: vi.fn(),
  deleteInstrumentPrice: vi.fn(),
}))

import { useAuth } from "@/contexts/AuthContext"

const mockLedger = makeLedger() // USD

// Local fixture factories (no dashboard-report factories in @/test/fixtures)
function makeBalanceSummary(balanceByCurrency: Record<string, number>): BalanceSummary {
  return { id: "balance-summary-1", balanceByCurrency }
}

function makeMonthlyReport(
  incomeByCurrency: Record<string, number>,
  expensesByCurrency: Record<string, number>
): MonthlyReport {
  return { id: "monthly-report-1", year: 2026, month: 7, incomeByCurrency, expensesByCurrency }
}

const mockTransaction: TransactionResource = {
  type: "transactions",
  id: "transaction-1",
  attributes: {
    date: "2026-07-10",
    description: "Grocery shopping",
    amount: 250.75,
    currency: "USD",
    entries: [
      { accountId: "account-1", accountName: "Groceries", amount: 250.75, currency: "USD", type: "DEBIT" },
      { accountId: "account-2", accountName: "Checking", amount: 250.75, currency: "USD", type: "CREDIT" },
    ],
    createdAt: "2026-07-10T00:00:00Z",
    updatedAt: "2026-07-10T00:00:00Z",
  },
}

// Happy-path defaults: one USD ledger with data for the summary cards and
// recent-transactions list, and empty chart datasets.
function mockDashboardApis() {
  vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
  vi.mocked(accountsApi.getBalanceSummary).mockResolvedValue(makeBalanceSummary({ USD: 1500.25 }))
  vi.mocked(transactionsApi.getTransactions).mockResolvedValue({ data: [mockTransaction] })
  vi.mocked(transactionsApi.getMonthlyReport).mockResolvedValue(
    makeMonthlyReport({ USD: 5000 }, { USD: 3200.5 })
  )
  // Chart children fan out these queries per ledger
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [] })
  vi.mocked(envelopesApi.getEnvelopes).mockResolvedValue({ data: [] })
  vi.mocked(envelopesApi.getBudgetSummary).mockResolvedValue({
    id: "budget-1",
    periodYear: 2026,
    periodMonth: 7,
    toBeBudgeted: 0,
    envelopeBalances: [],
  })
  vi.mocked(instrumentsApi.getPortfolio).mockResolvedValue({ data: [] })
}

const renderDashboard = () => renderWithProviders(<Dashboard />)

describe("Dashboard", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows the welcome sign-in card", () => {
      renderDashboard()

      expect(screen.getByText("Welcome to Ricash")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to manage your personal finances")).toBeInTheDocument()
      expect(
        screen.getByText("Track your income, expenses, and manage multiple ledgers with double-entry bookkeeping.")
      ).toBeInTheDocument()
    })
  })

  describe("when authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(
        mockUseAuth({ isAuthenticated: true, user: mockAuthenticatedUser, accessToken: "test-token" })
      )
    })

    it("renders summary cards from the mocked report APIs", async () => {
      mockDashboardApis()

      renderDashboard()

      expect(screen.getByText("Welcome back, Test User")).toBeInTheDocument()

      // Total balance aggregated from the balance-summary reports
      await waitFor(() => {
        expect(screen.getAllByText("$1,500.25").length).toBeGreaterThanOrEqual(1)
      })

      // Ledger count
      expect(screen.getByText("Total Ledgers")).toBeInTheDocument()
      expect(screen.getByText("1")).toBeInTheDocument()

      // Monthly income/expenses from the monthly report (en locale from setup)
      expect(screen.getAllByText("$5,000.00").length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText("$3,200.50").length).toBeGreaterThanOrEqual(1)
    })

    it("renders recent transactions and the ledger list", async () => {
      mockDashboardApis()

      renderDashboard()

      await waitFor(() => {
        expect(screen.getByText("Grocery shopping")).toBeInTheDocument()
      })

      expect(screen.getByText("Jul 10, 2026")).toBeInTheDocument()
      expect(screen.getAllByText("$250.75").length).toBeGreaterThanOrEqual(1)
      // Double-entry badges for the transaction's entries
      expect(screen.getByText("DB")).toBeInTheDocument()
      expect(screen.getByText("CR")).toBeInTheDocument()

      // Ledger list card
      expect(screen.getByText("Your Ledgers")).toBeInTheDocument()
      expect(screen.getByText("Personal Finance")).toBeInTheDocument()
    })

    it("renders the chart cards with empty-state labels when charts have no data", async () => {
      mockDashboardApis()

      renderDashboard()

      await waitFor(() => {
        expect(screen.getByText("Income vs Expenses")).toBeInTheDocument()
      })

      expect(screen.getByText("Expense Breakdown")).toBeInTheDocument()
      expect(screen.getByText("Budget Utilization")).toBeInTheDocument()
      expect(screen.getByText("Portfolio Allocation")).toBeInTheDocument()

      // Expense breakdown, budget utilization and portfolio allocation have no
      // data; income vs expenses does (the mocked monthly reports), so exactly
      // three empty labels.
      await waitFor(() => {
        expect(screen.getAllByText("No data available")).toHaveLength(3)
      })
    })

    it("shows zero totals and empty states when there are no ledgers", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [] })

      renderDashboard()

      await waitFor(() => {
        expect(screen.getByText("No ledgers yet. Create your first ledger to get started.")).toBeInTheDocument()
      })

      expect(screen.getByText("No recent transactions")).toBeInTheDocument()
      expect(screen.getByText("0")).toBeInTheDocument()
      // Balance, income and expenses all fall back to zero in the default BRL
      expect(screen.getAllByText(/R\$\s?0\.00/)).toHaveLength(3)

      // No charts without ledgers
      expect(screen.queryByText("Income vs Expenses")).not.toBeInTheDocument()
    })

    it("shows loading skeletons while queries are in flight", () => {
      // Never-resolving ledgers query keeps the dashboard in its loading state
      vi.mocked(ledgersApi.getLedgers).mockReturnValue(new Promise(() => {}))

      const { container } = renderDashboard()

      expect(screen.getByText("Welcome back, Test User")).toBeInTheDocument()
      // Skeletons instead of numbers or empty states
      expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0)
      expect(screen.queryByText(/R\$\s?0\.00/)).not.toBeInTheDocument()
      expect(screen.queryByText("No ledgers yet. Create your first ledger to get started.")).not.toBeInTheDocument()
    })

    it("still renders the other cards when one report query fails", async () => {
      // Silence the error log from the page's error handler
      const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
      mockDashboardApis()
      vi.mocked(accountsApi.getBalanceSummary).mockRejectedValue(new Error("boom"))

      renderDashboard()

      // Income/expenses still come through
      await waitFor(() => {
        expect(screen.getAllByText("$5,000.00").length).toBeGreaterThanOrEqual(1)
      })

      // The balance card falls back to a zero total in the ledger currency
      expect(screen.getAllByText("$0.00").length).toBeGreaterThanOrEqual(1)
      // The failure is reported through the error handler (toast + log)
      expect(consoleError).toHaveBeenCalled()

      consoleError.mockRestore()
    })
  })
})
