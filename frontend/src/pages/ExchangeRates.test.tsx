import { describe, it, expect, vi, beforeEach } from "vitest"
import { screen, waitFor, within, fireEvent } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { ExchangeRates } from "./ExchangeRates"
import * as exchangeRatesApi from "@/api/exchangeRates"
import type { ExchangeRateResource } from "@/api/types"
import { renderWithProviders, mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"

// Mock the auth context
vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

// Mock the APIs
vi.mock("@/api/exchangeRates", () => ({
  getExchangeRates: vi.fn(),
  createExchangeRate: vi.fn(),
  deleteExchangeRate: vi.fn(),
  fetchExchangeRate: vi.fn(),
}))

import { useAuth } from "@/contexts/AuthContext"

// Local fixture factory (exchange rates have no factory in @/test/fixtures)
function makeExchangeRate(
  overrides: Omit<Partial<ExchangeRateResource>, "attributes"> & {
    attributes?: Partial<ExchangeRateResource["attributes"]>
  } = {}
): ExchangeRateResource {
  const base: ExchangeRateResource = {
    type: "exchange-rates",
    id: "rate-1",
    attributes: {
      fromCurrency: "USD",
      toCurrency: "BRL",
      rate: 5.5,
      effectiveDate: "2026-01-15",
      source: "MANUAL",
      createdAt: "2026-01-10T00:00:00Z",
    },
  }
  return {
    ...base,
    ...overrides,
    attributes: { ...base.attributes, ...overrides.attributes },
  }
}

const mockRate = makeExchangeRate()

const renderExchangeRates = () => renderWithProviders(<ExchangeRates />)

describe("ExchangeRates", () => {
  describe("when not authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    })

    it("shows sign in required message", () => {
      renderExchangeRates()

      expect(screen.getByText("Sign in Required")).toBeInTheDocument()
      expect(screen.getByText("Please sign in to view your exchange rates")).toBeInTheDocument()
    })
  })

  describe("when authenticated", () => {
    beforeEach(() => {
      vi.mocked(useAuth).mockReturnValue(
        mockUseAuth({ isAuthenticated: true, user: mockAuthenticatedUser, accessToken: "test-token" })
      )
    })

    it("shows page title and requests the first page of rates", async () => {
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [] })

      renderExchangeRates()

      expect(screen.getByRole("heading", { name: "Exchange Rates" })).toBeInTheDocument()
      expect(screen.getByText("View historical exchange rate data")).toBeInTheDocument()

      await waitFor(() => {
        expect(exchangeRatesApi.getExchangeRates).toHaveBeenCalledWith({ "page[size]": 50 })
      })
    })

    it("displays rates with formatted values, dates and source badges", async () => {
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({
        data: [
          mockRate,
          makeExchangeRate({
            id: "rate-2",
            attributes: {
              fromCurrency: "EUR",
              toCurrency: "GBP",
              rate: 0.85123,
              effectiveDate: "2026-02-01",
              source: "EXTERNAL_API",
              createdAt: "2026-02-02T00:00:00Z",
            },
          }),
        ],
      })

      renderExchangeRates()

      await waitFor(() => {
        expect(screen.getByText("USD")).toBeInTheDocument()
      })

      expect(screen.getByText("BRL")).toBeInTheDocument()
      expect(screen.getByText("EUR")).toBeInTheDocument()
      expect(screen.getByText("GBP")).toBeInTheDocument()

      // Rates are rendered with 6 decimal places
      expect(screen.getByText("5.500000")).toBeInTheDocument()
      expect(screen.getByText("0.851230")).toBeInTheDocument()

      // Dates formatted for the en locale pinned in test setup
      expect(screen.getByText("Jan 15, 2026")).toBeInTheDocument()
      expect(screen.getByText("Jan 10, 2026")).toBeInTheDocument()
      expect(screen.getByText("Feb 1, 2026")).toBeInTheDocument()

      expect(screen.getByText("MANUAL")).toBeInTheDocument()
      expect(screen.getByText("EXTERNAL_API")).toBeInTheDocument()
    })

    it("shows empty state when there are no rates", async () => {
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [] })

      renderExchangeRates()

      await waitFor(() => {
        expect(screen.getByText("No exchange rates")).toBeInTheDocument()
      })

      expect(
        screen.getByText("Exchange rates will appear here when you create transactions with different currencies")
      ).toBeInTheDocument()
    })

    it("opens create dialog with a disabled submit until the form is valid", async () => {
      const user = userEvent.setup()
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [] })

      renderExchangeRates()

      await user.click(screen.getByRole("button", { name: /new rate/i }))

      expect(screen.getByRole("heading", { name: "Create Exchange Rate" })).toBeInTheDocument()
      expect(screen.getByLabelText("From")).toBeInTheDocument()
      expect(screen.getByLabelText("To")).toBeInTheDocument()
      expect(screen.getByLabelText("Rate")).toBeInTheDocument()
      expect(screen.getByLabelText("Effective Date")).toBeInTheDocument()
      expect(screen.getByRole("button", { name: "Create" })).toBeDisabled()
    })

    it("creates a rate when the form is submitted", async () => {
      const user = userEvent.setup()
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [] })
      vi.mocked(exchangeRatesApi.createExchangeRate).mockResolvedValueOnce({ data: mockRate })

      renderExchangeRates()

      await user.click(screen.getByRole("button", { name: /new rate/i }))

      // Currencies are uppercased by the input's onChange
      await user.type(screen.getByLabelText("From"), "usd")
      await user.type(screen.getByLabelText("To"), "brl")
      await user.type(screen.getByLabelText("Rate"), "5.5")
      fireEvent.change(screen.getByLabelText("Effective Date"), { target: { value: "2026-02-01" } })

      await user.click(screen.getByRole("button", { name: "Create" }))

      await waitFor(() => {
        expect(exchangeRatesApi.createExchangeRate).toHaveBeenCalledWith({
          fromCurrency: "USD",
          toCurrency: "BRL",
          rate: 5.5,
          effectiveDate: "2026-02-01",
        })
      })

      // Dialog closes on success
      await waitFor(() => {
        expect(screen.queryByRole("heading", { name: "Create Exchange Rate" })).not.toBeInTheDocument()
      })
    })

    it("deletes a rate after confirming", async () => {
      const user = userEvent.setup()
      vi.spyOn(window, "confirm").mockReturnValueOnce(true)
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [mockRate] })
      vi.mocked(exchangeRatesApi.deleteExchangeRate).mockResolvedValueOnce(undefined)

      renderExchangeRates()

      await waitFor(() => {
        expect(screen.getByText("5.500000")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Delete" }))

      await waitFor(() => {
        expect(exchangeRatesApi.deleteExchangeRate).toHaveBeenCalledWith("rate-1")
      })
    })

    it("does not delete when the confirmation is dismissed", async () => {
      const user = userEvent.setup()
      vi.spyOn(window, "confirm").mockReturnValueOnce(false)
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [mockRate] })

      renderExchangeRates()

      await waitFor(() => {
        expect(screen.getByText("5.500000")).toBeInTheDocument()
      })

      await user.click(screen.getByRole("button", { name: "Delete" }))

      expect(exchangeRatesApi.deleteExchangeRate).not.toHaveBeenCalled()
    })

    it("fetches a rate from the external provider", async () => {
      const user = userEvent.setup()
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [] })
      vi.mocked(exchangeRatesApi.fetchExchangeRate).mockResolvedValueOnce({ data: mockRate })

      renderExchangeRates()

      await user.click(screen.getByRole("button", { name: /fetch from api/i }))

      const dialog = screen.getByRole("dialog")
      expect(within(dialog).getByRole("heading", { name: "Fetch Exchange Rate" })).toBeInTheDocument()

      await user.type(within(dialog).getByLabelText("From"), "usd")
      await user.type(within(dialog).getByLabelText("To"), "brl")
      fireEvent.change(within(dialog).getByLabelText("Effective Date"), { target: { value: "2026-03-01" } })

      // The page header also has a "Fetch from API" button, so scope to the dialog
      await user.click(within(dialog).getByRole("button", { name: /fetch from api/i }))

      await waitFor(() => {
        expect(exchangeRatesApi.fetchExchangeRate).toHaveBeenCalledWith({
          fromCurrency: "USD",
          toCurrency: "BRL",
          date: "2026-03-01",
        })
      })
    })

    it("disables the fetch submit when both currencies are the same", async () => {
      const user = userEvent.setup()
      vi.mocked(exchangeRatesApi.getExchangeRates).mockResolvedValue({ data: [] })

      renderExchangeRates()

      await user.click(screen.getByRole("button", { name: /fetch from api/i }))

      const dialog = screen.getByRole("dialog")
      await user.type(within(dialog).getByLabelText("From"), "usd")
      await user.type(within(dialog).getByLabelText("To"), "usd")

      expect(within(dialog).getByRole("button", { name: /fetch from api/i })).toBeDisabled()

      expect(exchangeRatesApi.fetchExchangeRate).not.toHaveBeenCalled()
    })
  })
})
