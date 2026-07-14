import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Envelopes } from "./Envelopes"
import * as envelopesApi from "@/api/envelopes"
import * as accountsApi from "@/api/accounts"
import * as ledgersApi from "@/api/ledgers"
import type { EnvelopeResource } from "@/api/types"
import { renderWithProviders, mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"
import { makeLedger, makeAccount } from "@/test/fixtures"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the APIs (the page uses envelopes/accounts hooks and getEnvelopeAccounts
// directly; ledgers come in through useLedgerSelection)
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
      currency: "BRL",
      type: "EXPENSE",
      status: "ACTIVE",
      parentEnvelopeId: null,
      createdAt: "2024-01-01T00:00:00Z",
    },
  }
  return { ...base, ...overrides, attributes: { ...base.attributes, ...overrides?.attributes } }
}

const mockLedger = makeLedger()
const mockEnvelope = makeEnvelope()

function mockLedgerWith(envelopes: EnvelopeResource[], accounts = [makeAccount()]) {
  vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
  vi.mocked(envelopesApi.getEnvelopes).mockResolvedValue({ data: envelopes })
  vi.mocked(accountsApi.getAccounts).mockResolvedValue({ data: accounts })
}

const renderEnvelopes = () => renderWithProviders(<Envelopes />)

describe("Envelopes", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderEnvelopes()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your envelopes")).toBeInTheDocument()
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

      renderEnvelopes()

      expect(screen.getByRole("heading", { name: "Envelopes" })).toBeInTheDocument()
      expect(screen.getAllByText("Manage your budget envelopes").length).toBeGreaterThanOrEqual(1)

      await waitFor(() => {
        expect(screen.getAllByText("No ledger selected").length).toBeGreaterThanOrEqual(1)
      })
    })

    it("displays envelopes grouped by type", async () => {
      mockLedgerWith([
        makeEnvelope({
          id: "envelope-1",
          attributes: { name: "Groceries", type: "EXPENSE", currency: "USD" },
        }),
        makeEnvelope({
          id: "envelope-2",
          attributes: { name: "Salary", type: "INCOME", currency: "BRL" },
        }),
      ])

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
        expect(screen.getByText("Salary")).toBeInTheDocument()
      })

      // Group headers per type
      expect(screen.getByText("Expense Envelopes")).toBeInTheDocument()
      expect(screen.getByText("Income Targets")).toBeInTheDocument()
      // Type badges and currency cells
      expect(screen.getByText("Expense")).toBeInTheDocument()
      expect(screen.getByText("Income")).toBeInTheDocument()
      expect(screen.getByText("USD")).toBeInTheDocument()
      expect(screen.getByText("BRL")).toBeInTheDocument()
    })

    it("renders child envelopes expanded by default and collapses them", async () => {
      const user = userEvent.setup()
      mockLedgerWith([
        makeEnvelope({ id: "envelope-1", attributes: { name: "Food" } }),
        makeEnvelope({
          id: "envelope-2",
          attributes: { name: "Restaurants", parentEnvelopeId: "envelope-1" },
        }),
      ])

      renderEnvelopes()

      // Fresh lists arrive fully expanded, so the child is visible
      await waitFor(() => {
        expect(screen.getByText("Restaurants")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Collapse" }))

      expect(screen.queryByText("Restaurants")).not.toBeInTheDocument()
      expect(screen.getByText("Food")).toBeInTheDocument()
    })

    it("shows empty state when there are no envelopes", async () => {
      mockLedgerWith([])

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("No envelopes yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create your first envelope to start budgeting")).toBeInTheDocument()
    })

    it("opens create dialog when clicking New Envelope button", async () => {
      const user = userEvent.setup()
      mockLedgerWith([])

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("No envelopes yet")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: /new envelope/i }))

      expect(screen.getByRole("heading", { name: "Create Envelope" })).toBeInTheDocument()
      expect(screen.getByLabelText("Name")).toBeInTheDocument()
      expect(screen.getByLabelText("Currency")).toBeInTheDocument()
      // Currency defaults to BRL
      expect(screen.getByLabelText("Currency")).toHaveValue("BRL")
    })

    it("creates an envelope when form is submitted", async () => {
      const user = userEvent.setup()
      mockLedgerWith([])
      vi.mocked(envelopesApi.createEnvelope).mockResolvedValueOnce({ data: mockEnvelope })

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("No envelopes yet")).toBeInTheDocument()
      })

      // Open dialog
      await user.click(screen.getByRole("button", { name: /new envelope/i }))

      // Fill form (type stays at the EXPENSE default)
      await user.type(screen.getByLabelText("Name"), "Groceries")
      await user.clear(screen.getByLabelText("Currency"))
      await user.type(screen.getByLabelText("Currency"), "USD")
      await user.type(screen.getByLabelText(/description/i), "Monthly food budget")

      // Submit
      await user.click(screen.getByRole("button", { name: "Create Envelope" }))

      await waitFor(() => {
        expect(envelopesApi.createEnvelope).toHaveBeenCalledWith("personal-finance", {
          name: "Groceries",
          description: "Monthly food budget",
          currency: "USD",
          type: "EXPENSE",
          parentEnvelopeId: undefined,
        })
      })
    })

    it("edits an envelope through the row actions menu", async () => {
      const user = userEvent.setup()
      mockLedgerWith([mockEnvelope])
      vi.mocked(envelopesApi.updateEnvelope).mockResolvedValueOnce({ data: mockEnvelope })

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: "Edit" }))

      expect(screen.getByRole("heading", { name: "Edit Envelope" })).toBeInTheDocument()
      const nameInput = screen.getByLabelText("Name")
      expect(nameInput).toHaveValue("Groceries")

      await user.clear(nameInput)
      await user.type(nameInput, "Food")
      await user.click(screen.getByRole("button", { name: "Save" }))

      await waitFor(() => {
        expect(envelopesApi.updateEnvelope).toHaveBeenCalledWith("personal-finance", "envelope-1", {
          name: "Food",
          description: undefined,
          type: "EXPENSE",
          currency: "BRL",
          status: "ACTIVE",
          parentEnvelopeId: null,
        })
      })
    })

    it("deletes an envelope after confirmation", async () => {
      const user = userEvent.setup()
      const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true)
      mockLedgerWith([mockEnvelope])
      vi.mocked(envelopesApi.deleteEnvelope).mockResolvedValueOnce(undefined)

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: "Delete" }))

      expect(confirmSpy).toHaveBeenCalledWith("Are you sure you want to delete this envelope?")
      await waitFor(() => {
        expect(envelopesApi.deleteEnvelope).toHaveBeenCalledWith("personal-finance", "envelope-1")
      })

      confirmSpy.mockRestore()
    })

    it("does not delete when confirmation is dismissed", async () => {
      const user = userEvent.setup()
      const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(false)
      mockLedgerWith([mockEnvelope])

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: "Delete" }))

      expect(envelopesApi.deleteEnvelope).not.toHaveBeenCalled()

      confirmSpy.mockRestore()
    })

    it("links accounts to an envelope through the manage accounts dialog", async () => {
      const user = userEvent.setup()
      mockLedgerWith([mockEnvelope], [makeAccount()])
      vi.mocked(envelopesApi.getEnvelopeAccounts).mockResolvedValueOnce({ accountIds: [] })
      vi.mocked(envelopesApi.setEnvelopeAccounts).mockResolvedValueOnce({ accountIds: ["account-1"] })

      renderEnvelopes()

      await waitFor(() => {
        expect(screen.getByText("Groceries")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: "Manage Accounts" }))

      // Dialog loads the current mapping first
      expect(await screen.findByText("Manage Accounts for Groceries")).toBeInTheDocument()
      expect(envelopesApi.getEnvelopeAccounts).toHaveBeenCalledWith("personal-finance", "envelope-1")

      // Select the account and save
      await user.click(screen.getByRole("checkbox"))
      await user.click(screen.getByRole("button", { name: "Save" }))

      await waitFor(() => {
        expect(envelopesApi.setEnvelopeAccounts).toHaveBeenCalledWith("personal-finance", "envelope-1", [
          "account-1",
        ])
      })
    })
  })
})
