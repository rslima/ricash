import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { BrowserRouter } from "react-router-dom"
import { Accounts } from "./Accounts"
import * as accountsApi from "@/api/accounts"
import * as ledgersApi from "@/api/ledgers"
import type { AccountResource, LedgerResource } from "@/api/types"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the APIs
vi.mock("@/api/accounts", () => ({
  getAccounts: vi.fn(),
  createAccount: vi.fn(),
  deleteAccount: vi.fn(),
}))

vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
}))

import { useAuth } from "@/contexts/AuthContext"

const mockLedger: LedgerResource = {
  type: "ledgers",
  id: "ledger-1",
  attributes: {
    slug: "personal-finance",
    name: "Personal Finance",
    description: "My personal ledger",
    currency: "USD",
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  },
}

const mockAccount: AccountResource = {
  type: "accounts",
  id: "account-1",
  attributes: {
    slug: "checking-account",
    name: "Checking Account",
    type: "ASSET",
    currency: "USD",
    balance: 1000.5,
    description: "Main checking account",
    parentAccountId: null,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  },
}

function renderAccounts() {
  return render(
    <BrowserRouter>
      <Accounts />
    </BrowserRouter>
  )
}

describe("Accounts", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue({
        isAuthenticated: false,
        user: null,
        accessToken: null,
        isLoading: false,
        login: vi.fn(),
        logout: vi.fn(),
        startLogin: vi.fn(),
        exchangeCodeForToken: vi.fn(),
      })
    })

    it("shows sign in required message", () => {
      renderAccounts()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your accounts")).toBeInTheDocument()
    })
  })

  describe("when authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue({
        isAuthenticated: true,
        user: { id: "user-1", username: "testuser", email: "test@example.com", name: "Test User", roles: [] },
        accessToken: "test-token",
        isLoading: false,
        login: vi.fn(),
        logout: vi.fn(),
        startLogin: vi.fn(),
        exchangeCodeForToken: vi.fn(),
      })
    })

    it("shows page title", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({ data: [] })

      renderAccounts()

      expect(screen.getByText("Accounts")).toBeInTheDocument()
      expect(screen.getByText("Manage accounts within your ledgers")).toBeInTheDocument()
    })

    it("shows no ledger selected when there are no ledgers", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({ data: [] })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByText("No ledger selected")).toBeInTheDocument()
      })
    })

    it("loads and displays ledgers as buttons", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })
      vi.mocked(accountsApi.getAccounts).mockResolvedValueOnce({
        data: [],
      })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "Personal Finance" })).toBeInTheDocument()
      })
    })

    it("displays accounts after selecting a ledger", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })
      vi.mocked(accountsApi.getAccounts).mockResolvedValueOnce({
        data: [mockAccount],
      })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByText("Checking Account")).toBeInTheDocument()
      })

      expect(screen.getByText("ASSET")).toBeInTheDocument()
      expect(screen.getByText(/US\$\s*1\.000,50/)).toBeInTheDocument()
    })

    it("shows empty state when no accounts in ledger", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })
      vi.mocked(accountsApi.getAccounts).mockResolvedValueOnce({
        data: [],
      })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByText("No accounts yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create your first account to start tracking")).toBeInTheDocument()
    })

    it("opens create dialog when clicking New Account button", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })
      vi.mocked(accountsApi.getAccounts).mockResolvedValueOnce({
        data: [],
      })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByText("No accounts yet")).toBeInTheDocument()
      })

      const newAccountButton = screen.getByRole("button", { name: /new account/i })
      await user.click(newAccountButton)

      expect(screen.getByText("Create New Account")).toBeInTheDocument()
      expect(screen.getByLabelText("Name")).toBeInTheDocument()
      expect(screen.getByLabelText("Currency")).toBeInTheDocument()
    })

    it("creates an account when form is submitted", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })
      vi.mocked(accountsApi.getAccounts).mockResolvedValueOnce({
        data: [],
      })
      vi.mocked(accountsApi.createAccount).mockResolvedValueOnce({
        data: mockAccount,
      })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByText("No accounts yet")).toBeInTheDocument()
      })

      // Open dialog
      await user.click(screen.getByRole("button", { name: /new account/i }))

      // Fill form
      await user.type(screen.getByLabelText("Name"), "Checking Account")
      await user.clear(screen.getByLabelText("Currency"))
      await user.type(screen.getByLabelText("Currency"), "USD")

      // Submit
      await user.click(screen.getByRole("button", { name: /create account/i }))

      await waitFor(() => {
        expect(accountsApi.createAccount).toHaveBeenCalledWith("personal-finance", {
          name: "Checking Account",
          description: undefined,
          currency: "USD",
          type: "ASSET",
        })
      })
    })

    it("displays accounts of different types with correct badges", async () => {
      const accounts: AccountResource[] = [
        { ...mockAccount, id: "1", attributes: { ...mockAccount.attributes, name: "Cash", type: "ASSET" } },
        { ...mockAccount, id: "2", attributes: { ...mockAccount.attributes, name: "Credit Card", type: "LIABILITY" } },
        { ...mockAccount, id: "3", attributes: { ...mockAccount.attributes, name: "Salary", type: "INCOME" } },
        { ...mockAccount, id: "4", attributes: { ...mockAccount.attributes, name: "Groceries", type: "EXPENSE" } },
      ]

      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })
      vi.mocked(accountsApi.getAccounts).mockResolvedValueOnce({
        data: accounts,
      })

      renderAccounts()

      await waitFor(() => {
        expect(screen.getByText("Cash")).toBeInTheDocument()
        expect(screen.getByText("Credit Card")).toBeInTheDocument()
        expect(screen.getByText("Salary")).toBeInTheDocument()
        expect(screen.getByText("Groceries")).toBeInTheDocument()
      })

      expect(screen.getByText("ASSET")).toBeInTheDocument()
      expect(screen.getByText("LIABILITY")).toBeInTheDocument()
      expect(screen.getByText("INCOME")).toBeInTheDocument()
      expect(screen.getByText("EXPENSE")).toBeInTheDocument()
    })

    it("switches between ledgers", async () => {
      const user = userEvent.setup()
      const ledger2: LedgerResource = {
        type: "ledgers",
        id: "ledger-2",
        attributes: {
          slug: "business",
          name: "Business",
          description: null,
          currency: "EUR",
          createdAt: "2024-01-01T00:00:00Z",
          updatedAt: "2024-01-01T00:00:00Z",
        },
      }

      const account2: AccountResource = {
        ...mockAccount,
        id: "account-2",
        attributes: {
          ...mockAccount.attributes,
          name: "Business Account",
          currency: "EUR",
        },
      }

      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger, ledger2],
      })
      vi.mocked(accountsApi.getAccounts)
        .mockResolvedValueOnce({ data: [mockAccount] })
        .mockResolvedValueOnce({ data: [account2] })

      renderAccounts()

      // First ledger's account should be displayed
      await waitFor(() => {
        expect(screen.getByText("Checking Account")).toBeInTheDocument()
      })

      // Click on second ledger
      await user.click(screen.getByRole("button", { name: "Business" }))

      // Second ledger's account should be displayed
      await waitFor(() => {
        expect(screen.getByText("Business Account")).toBeInTheDocument()
      })

      expect(accountsApi.getAccounts).toHaveBeenCalledWith("business")
    })

    it("disables New Account button when no ledger is selected", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({ data: [] })

      renderAccounts()

      await waitFor(() => {
        const newAccountButton = screen.getByRole("button", { name: /new account/i })
        expect(newAccountButton).toBeDisabled()
      })
    })
  })
})
