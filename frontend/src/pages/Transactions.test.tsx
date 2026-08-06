import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { screen, waitFor, within, fireEvent } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Transactions } from "./Transactions"
import * as transactionsApi from "@/api/transactions"
import * as accountsApi from "@/api/accounts"
import * as ledgersApi from "@/api/ledgers"
import * as instrumentsApi from "@/api/instruments"
import type { TransactionResource } from "@/api/types"
import { renderWithProviders, mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"
import { makeLedger, makeAccount } from "@/test/fixtures"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the APIs (every function each *.hooks module imports must exist)
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

vi.mock("@/api/envelopes", () => ({
  getEnvelopes: vi.fn().mockResolvedValue({ data: [] }),
  getEnvelope: vi.fn(),
  getEnvelopeBalance: vi.fn(),
  getEnvelopeAccounts: vi.fn().mockResolvedValue([]),
  getBudgetSummary: vi.fn(),
  getEnvelopeMappings: vi.fn().mockResolvedValue({}),
  createEnvelope: vi.fn(),
  updateEnvelope: vi.fn(),
  deleteEnvelope: vi.fn(),
  allocateEnvelope: vi.fn(),
  setEnvelopeAccounts: vi.fn(),
}))

vi.mock("@/api/instruments", () => ({
  getInstruments: vi.fn(),
  getAllInstruments: vi.fn().mockResolvedValue([]),
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

const mockLedger = makeLedger()

const assetAccount = makeAccount()
const expenseAccount = makeAccount({
  id: "account-2",
  attributes: { slug: "groceries", name: "Groceries", type: "EXPENSE", balance: 0 },
})

// Local factory (fixtures.ts has no transaction factory).
type TransactionOverrides = Omit<Partial<TransactionResource>, "attributes"> & {
  attributes?: Partial<TransactionResource["attributes"]>
}

function makeTransaction(overrides?: TransactionOverrides): TransactionResource {
  const base: TransactionResource = {
    type: "transactions",
    id: "txn-1",
    attributes: {
      date: "2026-01-15",
      description: "Grocery shopping",
      amount: 1250.75,
      currency: "USD",
      entries: [
        { accountId: "account-1", accountName: "Checking Account", amount: 1250.75, currency: "USD", type: "CREDIT" },
        { accountId: "account-2", accountName: "Groceries", amount: 1250.75, currency: "USD", type: "DEBIT" },
      ],
      createdAt: "2026-01-15T00:00:00Z",
      updatedAt: "2026-01-15T00:00:00Z",
    },
  }
  return { ...base, ...overrides, attributes: { ...base.attributes, ...overrides?.attributes } }
}

const renderTransactions = () => renderWithProviders(<Transactions />)

describe("Transactions", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderTransactions()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your transactions")).toBeInTheDocument()
    })
  })

  describe("when authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(
        mockUseAuth({ isAuthenticated: true, user: mockAuthenticatedUser, accessToken: "test-token" })
      )
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: [assetAccount, expenseAccount] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([])
      vi.mocked(transactionsApi.getTransactionTemplates).mockResolvedValue([])
      vi.mocked(transactionsApi.getTransactions).mockResolvedValue({ data: [] })
    })

    afterEach(() => {
      vi.clearAllMocks()
    })

    it("displays transactions with formatted dates and amounts", async () => {
      vi.mocked(transactionsApi.getTransactions).mockResolvedValue({
        data: [
          makeTransaction(),
          makeTransaction({
            id: "txn-2",
            attributes: { date: "2026-02-03", description: "Monthly rent", amount: 900 },
          }),
        ],
      })

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("Grocery shopping")).toBeInTheDocument()
      })

      expect(screen.getByText("Monthly rent")).toBeInTheDocument()
      // en locale (pinned in test setup) formats dates as "Jan 15, 2026"
      expect(screen.getByText("Jan 15, 2026")).toBeInTheDocument()
      expect(screen.getByText("Feb 3, 2026")).toBeInTheDocument()
      // and USD amounts as $1,250.75
      expect(screen.getByText(/\$\s*1,250\.75/)).toBeInTheDocument()
      expect(screen.getByText(/\$\s*900\.00/)).toBeInTheDocument()
      // double-entry badges render account name and entry side
      expect(screen.getAllByText(/Checking Account: CR/).length).toBeGreaterThanOrEqual(1)
      expect(screen.getAllByText(/Groceries: DB/).length).toBeGreaterThanOrEqual(1)
    })

    it("shows empty state when the ledger has no transactions", async () => {
      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("No transactions yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create your first transaction to track your finances")).toBeInTheDocument()
    })

    it("opens the create dialog when clicking New Transaction", async () => {
      const user = userEvent.setup()

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("No transactions yet")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: /new transaction/i }))

      expect(screen.getByRole("heading", { name: "Create Transaction" })).toBeInTheDocument()
      expect(screen.getByText("Credit Entries")).toBeInTheDocument()
      expect(screen.getByText("Debit Entries")).toBeInTheDocument()
      expect(screen.getByLabelText("Date")).toBeInTheDocument()
    })

    it("creates a balanced transaction and submits the payload", async () => {
      const user = userEvent.setup()
      vi.mocked(transactionsApi.createTransaction).mockResolvedValueOnce({ data: makeTransaction() })

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("No transactions yet")).toBeInTheDocument()
      })

      // Open dialog
      await user.click(screen.getByRole("button", { name: /new transaction/i }))
      const dialog = screen.getByRole("dialog")

      // Date
      fireEvent.change(within(dialog).getByLabelText("Date"), { target: { value: "2026-03-10" } })

      // Comboboxes in DOM order: [description, credit account, debit account].
      // The combobox role takes no name from content, so query by position.
      // Credit entry account
      await user.click(within(dialog).getAllByRole("combobox")[1])
      await user.click(screen.getByText("Checking Account"))

      // Debit entry account
      await user.click(within(dialog).getAllByRole("combobox")[2])
      await user.click(screen.getByText("Groceries"))

      // Entries seed with the default currency (BRL); set both to the accounts' USD
      while (within(dialog).queryAllByDisplayValue("BRL").length > 0) {
        const input = within(dialog).getAllByDisplayValue("BRL")[0]
        await user.clear(input)
        await user.type(input, "USD")
      }

      // The lone credit amount is read-only; typing the debit derives it.
      const amountInputs = within(dialog).getAllByPlaceholderText("0.00")
      expect(amountInputs[0]).toBeDisabled()
      await user.type(amountInputs[1], "50")

      await waitFor(() => {
        expect(within(dialog).getAllByPlaceholderText("0.00")[0]).toHaveValue(50)
      })

      // Description via the autocomplete popover
      await user.click(within(dialog).getAllByRole("combobox")[0])
      await user.type(screen.getByPlaceholderText("Description"), "Lunch")
      await user.keyboard("{Escape}")

      // Submit
      await user.click(within(dialog).getByRole("button", { name: "Create Transaction" }))

      await waitFor(() => {
        expect(transactionsApi.createTransaction).toHaveBeenCalledWith("personal-finance", {
          date: "2026-03-10",
          description: "Lunch",
          entries: [
            { accountId: "account-1", amount: 50, currency: "USD", type: "CREDIT" },
            { accountId: "account-2", amount: 50, currency: "USD", type: "DEBIT" },
          ],
        })
      })
    })

    it("deletes a transaction after confirmation", async () => {
      const user = userEvent.setup()
      const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true)
      vi.mocked(transactionsApi.getTransactions).mockResolvedValue({ data: [makeTransaction()] })
      vi.mocked(transactionsApi.deleteTransaction).mockResolvedValueOnce(undefined)

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("Grocery shopping")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: /delete/i }))

      expect(confirmSpy).toHaveBeenCalledWith("Are you sure you want to delete this transaction?")
      await waitFor(() => {
        expect(transactionsApi.deleteTransaction).toHaveBeenCalledWith("personal-finance", "txn-1")
      })

      confirmSpy.mockRestore()
    })

    it("does not delete when the confirmation is dismissed", async () => {
      const user = userEvent.setup()
      const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false)
      vi.mocked(transactionsApi.getTransactions).mockResolvedValue({ data: [makeTransaction()] })

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("Grocery shopping")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: /delete/i }))

      expect(transactionsApi.deleteTransaction).not.toHaveBeenCalled()

      confirmSpy.mockRestore()
    })

    it("shows pagination controls and fetches the next page", async () => {
      const user = userEvent.setup()
      vi.mocked(transactionsApi.getTransactions).mockResolvedValue({
        data: [makeTransaction()],
        meta: { page: { number: 0, size: 20, totalElements: 50, totalPages: 3 } },
      })

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("Grocery shopping")).toBeInTheDocument()
      })

      expect(screen.getByText("50 transaction(s)")).toBeInTheDocument()
      expect(screen.getByText("Page 1 of 3")).toBeInTheDocument()

      await user.click(screen.getByRole("button", { name: "Next page" }))

      await waitFor(() => {
        expect(transactionsApi.getTransactions).toHaveBeenCalledWith("personal-finance", {
          "page[number]": 1,
          "page[size]": 20,
        })
      })
    })

    it("opens the export dialog from the Export button", async () => {
      const user = userEvent.setup()

      renderTransactions()

      await waitFor(() => {
        expect(screen.getByText("No transactions yet")).toBeInTheDocument()
      })

      const exportButton = screen.getByRole("button", { name: "Export" })
      expect(exportButton).toBeEnabled()
      await user.click(exportButton)

      expect(screen.getByRole("heading", { name: "Export transactions" })).toBeInTheDocument()
    })
  })
})
