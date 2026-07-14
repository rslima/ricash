import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Instruments } from "./Instruments"
import * as instrumentsApi from "@/api/instruments"
import * as ledgersApi from "@/api/ledgers"
import type { InstrumentResource } from "@/api/types"
import { renderWithProviders, mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"
import { makeLedger } from "@/test/fixtures"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the APIs (instruments.hooks imports every named export below)
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

vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
}))

import { useAuth } from "@/contexts/AuthContext"

// Local fixture: instruments are not covered by @/test/fixtures.
type InstrumentOverrides = Omit<Partial<InstrumentResource>, "attributes"> & {
  attributes?: Partial<InstrumentResource["attributes"]>
}

function makeInstrument(overrides?: InstrumentOverrides): InstrumentResource {
  const base: InstrumentResource = {
    type: "instruments",
    id: "instrument-1",
    attributes: {
      ledgerId: "ledger-1",
      symbol: "PETR4",
      name: "Petrobras PN",
      type: "STOCK",
      currency: "BRL",
      market: "B3",
      isin: "BRPETRACNPR6",
      status: "ACTIVE",
      createdAt: "2024-01-01T00:00:00Z",
    },
  }
  return {
    ...base,
    ...overrides,
    attributes: { ...base.attributes, ...overrides?.attributes },
  }
}

const mockLedger = makeLedger()
const mockInstrument = makeInstrument()

const renderInstruments = () => renderWithProviders(<Instruments />)

describe("Instruments", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderInstruments()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your instruments")).toBeInTheDocument()
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

      renderInstruments()

      expect(screen.getByRole("heading", { name: "Instruments" })).toBeInTheDocument()
      expect(screen.getByText("Manage your financial instruments")).toBeInTheDocument()

      await waitFor(() => {
        expect(screen.getByText("No ledger selected")).toBeInTheDocument()
      })
    })

    it("displays instruments for the selected ledger", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [mockInstrument] })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      expect(screen.getByText("Petrobras PN")).toBeInTheDocument()
      expect(screen.getByText("Stock")).toBeInTheDocument()
      expect(screen.getByText("BRL")).toBeInTheDocument()
      expect(screen.getByText("B3")).toBeInTheDocument()
      expect(screen.getByText("Active")).toBeInTheDocument()

      expect(instrumentsApi.getInstruments).toHaveBeenCalledWith("personal-finance", { "page[size]": 100 })
    })

    it("shows empty state when the ledger has no instruments", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [] })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("No instruments yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create your first instrument to start tracking investments")).toBeInTheDocument()
    })

    it("opens the create dialog when clicking New Instrument", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [] })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("No instruments yet")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: /new instrument/i }))

      expect(screen.getByRole("heading", { name: "Create Instrument" })).toBeInTheDocument()
      expect(screen.getByLabelText("Symbol")).toBeInTheDocument()
      expect(screen.getByLabelText("Name")).toBeInTheDocument()
      expect(screen.getByLabelText("Currency")).toBeInTheDocument()
    })

    it("creates an instrument when the form is submitted", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [] })
      vi.mocked(instrumentsApi.createInstrument).mockResolvedValueOnce({ data: mockInstrument })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("No instruments yet")).toBeInTheDocument()
      })

      // Open dialog
      await user.click(screen.getByRole("button", { name: /new instrument/i }))

      // Fill form (symbol is uppercased by the input; type defaults to STOCK, currency to BRL)
      await user.type(screen.getByLabelText("Symbol"), "petr4")
      await user.type(screen.getByLabelText("Name"), "Petrobras PN")
      await user.type(screen.getByLabelText(/Market/), "B3")

      // Submit
      await user.click(screen.getByRole("button", { name: "Create" }))

      await waitFor(() => {
        expect(instrumentsApi.createInstrument).toHaveBeenCalledWith("personal-finance", {
          symbol: "PETR4",
          name: "Petrobras PN",
          type: "STOCK",
          currency: "BRL",
          market: "B3",
          isin: undefined,
        })
      })
    })

    it("edits an instrument through the actions menu", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [mockInstrument] })
      vi.mocked(instrumentsApi.updateInstrument).mockResolvedValueOnce({
        data: makeInstrument({ attributes: { name: "Petrobras Preferencial" } }),
      })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      // Open the row actions dropdown and pick Edit
      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: /edit/i }))

      expect(screen.getByRole("heading", { name: "Edit Instrument" })).toBeInTheDocument()

      // Form is prefilled from the instrument
      const nameInput = screen.getByLabelText("Name")
      expect(nameInput).toHaveValue("Petrobras PN")

      await user.clear(nameInput)
      await user.type(nameInput, "Petrobras Preferencial")

      await user.click(screen.getByRole("button", { name: "Save" }))

      await waitFor(() => {
        expect(instrumentsApi.updateInstrument).toHaveBeenCalledWith("personal-finance", "instrument-1", {
          symbol: "PETR4",
          name: "Petrobras Preferencial",
          type: "STOCK",
          currency: "BRL",
          market: "B3",
          isin: "BRPETRACNPR6",
          status: "ACTIVE",
        })
      })
    })

    it("deletes an instrument after confirmation", async () => {
      const user = userEvent.setup()
      vi.spyOn(window, "confirm").mockReturnValue(true)
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [mockInstrument] })
      vi.mocked(instrumentsApi.deleteInstrument).mockResolvedValueOnce(undefined)

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: /delete/i }))

      await waitFor(() => {
        expect(instrumentsApi.deleteInstrument).toHaveBeenCalledWith("personal-finance", "instrument-1")
      })
    })

    it("does not delete an instrument when confirmation is dismissed", async () => {
      const user = userEvent.setup()
      vi.spyOn(window, "confirm").mockReturnValue(false)
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstruments).mockResolvedValue({ data: [mockInstrument] })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Actions" }))
      await user.click(await screen.findByRole("menuitem", { name: /delete/i }))

      expect(instrumentsApi.deleteInstrument).not.toHaveBeenCalled()
    })

    it("disables New Instrument button when no ledger is selected", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [] })

      renderInstruments()

      await waitFor(() => {
        expect(screen.getByRole("button", { name: /new instrument/i })).toBeDisabled()
      })
    })
  })
})
