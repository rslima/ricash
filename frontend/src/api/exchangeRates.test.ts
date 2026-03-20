import { describe, it, expect, vi, beforeEach } from "vitest"
import { getExchangeRates, createExchangeRate, deleteExchangeRate } from "./exchangeRates"
import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, ExchangeRateResource } from "./types"

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockRate: ExchangeRateResource = {
  type: "exchange-rates",
  id: "rate-1",
  attributes: {
    fromCurrency: "USD",
    toCurrency: "BRL",
    rate: 5.5,
    effectiveDate: "2026-01-15",
    source: "MANUAL",
    createdAt: "2026-01-15T00:00:00Z",
  },
}

describe("getExchangeRates", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches exchange rates", async () => {
    const mockResponse: JsonApiListResponse<ExchangeRateResource> = { data: [mockRate] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getExchangeRates()

    expect(apiClient.get).toHaveBeenCalledWith("/exchange-rates", undefined)
    expect(result.data).toHaveLength(1)
    expect(result.data[0].attributes.fromCurrency).toBe("USD")
  })
})

describe("createExchangeRate", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates an exchange rate", async () => {
    const mockResponse: JsonApiResponse<ExchangeRateResource> = { data: mockRate }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const input = {
      fromCurrency: "USD",
      toCurrency: "BRL",
      rate: 5.5,
      effectiveDate: "2026-01-15",
    }
    const result = await createExchangeRate(input)

    expect(apiClient.post).toHaveBeenCalledWith("/exchange-rates", {
      fromCurrency: "USD",
      toCurrency: "BRL",
      rate: 5.5,
      effectiveDate: "2026-01-15",
    })
    expect(result.data.attributes.rate).toBe(5.5)
  })
})

describe("deleteExchangeRate", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes an exchange rate", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteExchangeRate("rate-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/exchange-rates/rate-1")
  })
})
