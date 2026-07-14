import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor, fireEvent } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { InstrumentPrices } from "./InstrumentPrices"
import * as instrumentsApi from "@/api/instruments"
import * as ledgersApi from "@/api/ledgers"
import type { InstrumentResource, InstrumentPriceResource } from "@/api/types"
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

// Local fixtures: instruments and prices are not covered by @/test/fixtures.
type ResourceOverrides<T extends { attributes: object }> = Omit<Partial<T>, "attributes"> & {
  attributes?: Partial<T["attributes"]>
}

function makeInstrument(overrides?: ResourceOverrides<InstrumentResource>): InstrumentResource {
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
      status: "ACTIVE",
      createdAt: "2024-01-01T00:00:00Z",
    },
  }
  return { ...base, ...overrides, attributes: { ...base.attributes, ...overrides?.attributes } }
}

function makePrice(overrides?: ResourceOverrides<InstrumentPriceResource>): InstrumentPriceResource {
  const base: InstrumentPriceResource = {
    type: "instrument-prices",
    id: "price-1",
    attributes: {
      instrumentId: "instrument-1",
      instrumentSymbol: "PETR4",
      price: 35.5,
      effectiveDate: "2026-01-15",
      source: "MANUAL",
      createdAt: "2026-01-20T00:00:00Z",
    },
  }
  return { ...base, ...overrides, attributes: { ...base.attributes, ...overrides?.attributes } }
}

const mockLedger = makeLedger()
const mockInstrument = makeInstrument()
const mockPrice = makePrice()

const renderInstrumentPrices = () => renderWithProviders(<InstrumentPrices />)

describe("InstrumentPrices", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderInstrumentPrices()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your prices")).toBeInTheDocument()
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

      renderInstrumentPrices()

      expect(screen.getByRole("heading", { name: "Instrument Prices" })).toBeInTheDocument()
      expect(screen.getByText("Manage historical prices for instruments")).toBeInTheDocument()

      await waitFor(() => {
        expect(screen.getByText("No ledger selected")).toBeInTheDocument()
      })
    })

    it("displays prices with formatted date and currency amounts", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstrumentPrices).mockResolvedValue({ data: [mockPrice] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([mockInstrument])

      renderInstrumentPrices()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      // en locale (pinned in test setup): BRL amount and short date formats
      expect(screen.getByText(/R\$\s*35\.50/)).toBeInTheDocument()
      expect(screen.getByText("Jan 15, 2026")).toBeInTheDocument()
      expect(screen.getByText("Jan 20, 2026")).toBeInTheDocument()
      expect(screen.getByText("MANUAL")).toBeInTheDocument()

      // The list is requested with a fixed first page of 100 rows (no pager UI)
      expect(instrumentsApi.getInstrumentPrices).toHaveBeenCalledWith("personal-finance", { "page[size]": 100 })
    })

    it("shows empty state when there are no prices", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstrumentPrices).mockResolvedValue({ data: [] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([mockInstrument])

      renderInstrumentPrices()

      await waitFor(() => {
        expect(screen.getByText("No prices yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Add price entries to track your portfolio value")).toBeInTheDocument()
    })

    it("disables New Price button when the ledger has no instruments", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstrumentPrices).mockResolvedValue({ data: [] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([])

      renderInstrumentPrices()

      await waitFor(() => {
        expect(screen.getByText("No prices yet")).toBeInTheDocument()
      })

      expect(screen.getByRole("button", { name: /new price/i })).toBeDisabled()
    })

    it("creates a price when the form is submitted", async () => {
      const user = userEvent.setup()
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstrumentPrices).mockResolvedValue({ data: [] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([mockInstrument])
      vi.mocked(instrumentsApi.createInstrumentPrice).mockResolvedValueOnce({ data: mockPrice })

      renderInstrumentPrices()

      await waitFor(() => {
        expect(screen.getByText("No prices yet")).toBeInTheDocument()
      })

      // Open dialog
      await user.click(screen.getByRole("button", { name: /new price/i }))
      expect(screen.getByRole("heading", { name: "Add Price" })).toBeInTheDocument()

      // Pick the instrument in the select
      await user.click(screen.getByRole("combobox"))
      await user.click(await screen.findByRole("option", { name: "PETR4 - Petrobras PN" }))

      // Price and effective date
      await user.type(screen.getByLabelText("Price"), "35.50")
      fireEvent.change(screen.getByLabelText("Effective Date"), { target: { value: "2026-01-15" } })

      // Submit
      await user.click(screen.getByRole("button", { name: "Create" }))

      await waitFor(() => {
        expect(instrumentsApi.createInstrumentPrice).toHaveBeenCalledWith("personal-finance", {
          instrumentId: "instrument-1",
          price: 35.5,
          effectiveDate: "2026-01-15",
        })
      })
    })

    it("deletes a price after confirmation", async () => {
      const user = userEvent.setup()
      vi.spyOn(window, "confirm").mockReturnValue(true)
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstrumentPrices).mockResolvedValue({ data: [mockPrice] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([mockInstrument])
      vi.mocked(instrumentsApi.deleteInstrumentPrice).mockResolvedValueOnce(undefined)

      renderInstrumentPrices()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Delete" }))

      await waitFor(() => {
        expect(instrumentsApi.deleteInstrumentPrice).toHaveBeenCalledWith("personal-finance", "price-1")
      })
    })

    it("does not delete a price when confirmation is dismissed", async () => {
      const user = userEvent.setup()
      vi.spyOn(window, "confirm").mockReturnValue(false)
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getInstrumentPrices).mockResolvedValue({ data: [mockPrice] })
      vi.mocked(instrumentsApi.getAllInstruments).mockResolvedValue([mockInstrument])

      renderInstrumentPrices()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Delete" }))

      expect(instrumentsApi.deleteInstrumentPrice).not.toHaveBeenCalled()
    })
  })
})
