import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { BrowserRouter } from "react-router-dom"
import { Ledgers } from "./Ledgers"
import * as ledgersApi from "@/api/ledgers"
import type { LedgerResource } from "@/api/types"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the ledgers API
vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
  createLedger: vi.fn(),
  deleteLedger: vi.fn(),
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

function renderLedgers() {
  return render(
    <BrowserRouter>
      <Ledgers />
    </BrowserRouter>
  )
}

describe("Ledgers", () => {
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
        logout: vi.fn(),
        startLogin: vi.fn(),
        exchangeCodeForToken: vi.fn(),
      })
    })

    it("shows sign in required message", () => {
      renderLedgers()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your ledgers")).toBeInTheDocument()
    })
  })

  describe("when authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue({
        isAuthenticated: true,
        user: { id: "user-1", username: "testuser", email: "test@example.com", name: "Test User", roles: [] },
        accessToken: "test-token",
        isLoading: false,
        logout: vi.fn(),
        startLogin: vi.fn(),
        exchangeCodeForToken: vi.fn(),
      })
    })

    it("shows loading state initially", () => {
      vi.mocked(ledgersApi.getLedgers).mockImplementation(() => new Promise(() => {}))

      renderLedgers()

      expect(screen.getByText("Ledgers")).toBeInTheDocument()
    })

    it("displays ledgers after loading", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger],
      })

      renderLedgers()

      await waitFor(() => {
        expect(screen.getByText("Personal Finance")).toBeInTheDocument()
      })

      expect(screen.getByText("USD")).toBeInTheDocument()
      expect(screen.getByText("My personal ledger")).toBeInTheDocument()
    })

    it("shows empty state when no ledgers", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [],
      })

      renderLedgers()

      await waitFor(() => {
        expect(screen.getByText("No ledgers yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create your first ledger to start tracking your finances")).toBeInTheDocument()
    })

    it("opens create dialog when clicking New Ledger button", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [],
      })

      renderLedgers()

      await waitFor(() => {
        expect(screen.getByText("No ledgers yet")).toBeInTheDocument()
      })

      const newLedgerButton = screen.getByRole("button", { name: /new ledger/i })
      await user.click(newLedgerButton)

      expect(screen.getByText("Create New Ledger")).toBeInTheDocument()
      expect(screen.getByLabelText("Name")).toBeInTheDocument()
      expect(screen.getByLabelText("Currency")).toBeInTheDocument()
    })

    it("creates a ledger when form is submitted", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [],
      })
      vi.mocked(ledgersApi.createLedger).mockResolvedValueOnce({
        data: mockLedger,
      })

      renderLedgers()

      await waitFor(() => {
        expect(screen.getByText("No ledgers yet")).toBeInTheDocument()
      })

      // Open dialog
      await user.click(screen.getByRole("button", { name: /new ledger/i }))

      // Fill form
      await user.type(screen.getByLabelText("Name"), "Personal Finance")
      await user.clear(screen.getByLabelText("Currency"))
      await user.type(screen.getByLabelText("Currency"), "USD")

      // Submit
      await user.click(screen.getByRole("button", { name: /create ledger/i }))

      await waitFor(() => {
        expect(ledgersApi.createLedger).toHaveBeenCalledWith({
          name: "Personal Finance",
          description: undefined,
          currency: "USD",
        })
      })
    })

    it("displays multiple ledgers", async () => {
      const ledger2: LedgerResource = {
        type: "ledgers",
        id: "ledger-2",
        attributes: {
          slug: "business",
          name: "Business",
          description: "Business expenses",
          currency: "EUR",
          createdAt: "2024-02-01T00:00:00Z",
          updatedAt: "2024-02-01T00:00:00Z",
        },
      }

      vi.mocked(ledgersApi.getLedgers).mockResolvedValueOnce({
        data: [mockLedger, ledger2],
      })

      renderLedgers()

      await waitFor(() => {
        expect(screen.getByText("Personal Finance")).toBeInTheDocument()
        expect(screen.getByText("Business")).toBeInTheDocument()
      })

      expect(screen.getByText("USD")).toBeInTheDocument()
      expect(screen.getByText("EUR")).toBeInTheDocument()
    })
  })
})
