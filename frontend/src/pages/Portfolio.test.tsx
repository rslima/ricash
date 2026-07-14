import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import { Portfolio } from "./Portfolio"
import * as instrumentsApi from "@/api/instruments"
import * as ledgersApi from "@/api/ledgers"
import type { InstrumentPositionResource } from "@/api/types"
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

// Local fixture: portfolio positions are not covered by @/test/fixtures.
type PositionOverrides = Omit<Partial<InstrumentPositionResource>, "attributes"> & {
  attributes?: Partial<InstrumentPositionResource["attributes"]>
}

function makePosition(overrides?: PositionOverrides): InstrumentPositionResource {
  const base: InstrumentPositionResource = {
    type: "positions",
    id: "position-1",
    attributes: {
      instrumentId: "instrument-1",
      instrumentSymbol: "PETR4",
      instrumentName: "Petrobras PN",
      instrumentType: "STOCK",
      currency: "BRL",
      quantity: 100,
      totalCost: 3000,
      averageCost: 30,
      currentPrice: 35.5,
      currentValue: 3550,
      unrealizedGain: 550,
      unrealizedGainPercent: 18.33,
    },
  }
  return { ...base, ...overrides, attributes: { ...base.attributes, ...overrides?.attributes } }
}

const mockLedger = makeLedger()
const mockPosition = makePosition()

const renderPortfolio = () => renderWithProviders(<Portfolio />)

describe("Portfolio", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderPortfolio()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your portfolio")).toBeInTheDocument()
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

      renderPortfolio()

      expect(screen.getByRole("heading", { name: "Portfolio" })).toBeInTheDocument()
      expect(screen.getByText("View your investment positions and performance")).toBeInTheDocument()

      await waitFor(() => {
        expect(screen.getByText("No ledger selected")).toBeInTheDocument()
      })
    })

    it("displays positions with formatted amounts", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getPortfolio).mockResolvedValue({ data: [mockPosition] })

      renderPortfolio()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      expect(screen.getByText("Petrobras PN")).toBeInTheDocument()
      expect(screen.getByText("Stock")).toBeInTheDocument()
      expect(screen.getByText("100")).toBeInTheDocument()

      // en locale (pinned in test setup) formats BRL as R$...
      expect(screen.getByText(/R\$\s*30\.00/)).toBeInTheDocument()
      expect(screen.getByText(/R\$\s*35\.50/)).toBeInTheDocument()
      // Total cost and current value appear in both the summary cards and the table row
      expect(screen.getAllByText(/R\$\s*3,000\.00/).length).toBeGreaterThanOrEqual(2)
      expect(screen.getAllByText(/R\$\s*3,550\.00/).length).toBeGreaterThanOrEqual(2)

      expect(instrumentsApi.getPortfolio).toHaveBeenCalledWith("personal-finance")
    })

    it("shows summary totals including unrealized gain and percentage", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getPortfolio).mockResolvedValue({ data: [mockPosition] })

      renderPortfolio()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      // Summary card copy
      expect(screen.getByText("Total invested amount")).toBeInTheDocument()
      expect(screen.getByText("Based on latest prices")).toBeInTheDocument()
      expect(screen.getByText("Unrealized Gain")).toBeInTheDocument()

      // Gain shows in the card and in the table row, sign-prefixed
      expect(screen.getAllByText(/\+R\$\s*550\.00/).length).toBeGreaterThanOrEqual(2)
      // Card percent derives from totals (550/3000), row percent from the attribute
      expect(screen.getAllByText("+18.33%").length).toBeGreaterThanOrEqual(2)
    })

    it("aggregates totals per currency", async () => {
      const usdPosition = makePosition({
        id: "position-2",
        attributes: {
          instrumentId: "instrument-2",
          instrumentSymbol: "VOO",
          instrumentName: "Vanguard S&P 500",
          instrumentType: "ETF",
          currency: "USD",
          quantity: 10,
          totalCost: 4000,
          averageCost: 400,
          currentPrice: 450,
          currentValue: 4500,
          unrealizedGain: 500,
          unrealizedGainPercent: 12.5,
        },
      })
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getPortfolio).mockResolvedValue({ data: [mockPosition, usdPosition] })

      renderPortfolio()

      await waitFor(() => {
        expect(screen.getByText("VOO")).toBeInTheDocument()
      })

      // Each currency keeps its own total line in the summary cards
      expect(screen.getAllByText(/R\$\s*3,000\.00/).length).toBeGreaterThanOrEqual(2)
      expect(screen.getAllByText(/\$\s*4,000\.00/).length).toBeGreaterThanOrEqual(2)
      expect(screen.getAllByText(/R\$\s*3,550\.00/).length).toBeGreaterThanOrEqual(2)
      expect(screen.getAllByText(/\$\s*4,500\.00/).length).toBeGreaterThanOrEqual(2)
    })

    it("shows dashes and add-prices hints for positions without a current price", async () => {
      const noPricePosition = makePosition({
        attributes: {
          currentPrice: undefined,
          currentValue: undefined,
          unrealizedGain: undefined,
          unrealizedGainPercent: undefined,
        },
      })
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getPortfolio).mockResolvedValue({ data: [noPricePosition] })

      renderPortfolio()

      await waitFor(() => {
        expect(screen.getByText("PETR4")).toBeInTheDocument()
      })

      // Cards: current value and gain placeholders plus explanatory copy
      expect(screen.getByText("Add prices to calculate")).toBeInTheDocument()
      expect(screen.getByText("Add instrument prices to see performance")).toBeInTheDocument()
      // Table row: current price, current value, and gain cells fall back to "-"
      expect(screen.getAllByText("-").length).toBeGreaterThanOrEqual(3)
    })

    it("shows empty state without summary cards when there are no positions", async () => {
      vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [mockLedger] })
      vi.mocked(instrumentsApi.getPortfolio).mockResolvedValue({ data: [] })

      renderPortfolio()

      await waitFor(() => {
        expect(screen.getByText("No positions yet")).toBeInTheDocument()
      })

      expect(screen.getByText("Create transactions with instruments to see your positions here")).toBeInTheDocument()
      expect(screen.queryByText("Total invested amount")).not.toBeInTheDocument()
    })
  })
})
