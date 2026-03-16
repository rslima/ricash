import { describe, it, expect, vi, beforeEach } from "vitest"
import {
  getInstruments,
  getAllInstruments,
  getInstrument,
  createInstrument,
  updateInstrument,
  deleteInstrument,
  getInstrumentPrices,
  createInstrumentPrice,
  deleteInstrumentPrice,
  getPortfolio,
  getAccountPortfolio,
} from "./instruments"
import { apiClient } from "./client"
import type {
  JsonApiListResponse,
  JsonApiResponse,
  InstrumentResource,
  InstrumentPriceResource,
  InstrumentPositionResource,
} from "./types"

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockInstrument: InstrumentResource = {
  type: "instruments",
  id: "instr-1",
  attributes: {
    ledgerId: "ledger-1",
    symbol: "PETR4",
    name: "Petrobras PN",
    type: "STOCK",
    currency: "BRL",
    market: "B3",
    status: "ACTIVE",
    createdAt: "2026-01-01T00:00:00Z",
  },
}

const mockPrice: InstrumentPriceResource = {
  type: "instrument-prices",
  id: "price-1",
  attributes: {
    instrumentId: "instr-1",
    instrumentSymbol: "PETR4",
    price: 35.5,
    effectiveDate: "2026-01-15",
    source: "MANUAL",
    createdAt: "2026-01-15T00:00:00Z",
  },
}

const mockPosition: InstrumentPositionResource = {
  type: "positions",
  id: "pos-1",
  attributes: {
    instrumentId: "instr-1",
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

describe("getInstruments", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches instruments without pagination", async () => {
    const mockResponse: JsonApiListResponse<InstrumentResource> = { data: [mockInstrument] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getInstruments("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/instruments", undefined)
    expect(result.data).toHaveLength(1)
  })

  it("fetches instruments with pagination", async () => {
    const mockResponse: JsonApiListResponse<InstrumentResource> = { data: [mockInstrument] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    await getInstruments("my-ledger", { "page[number]": 0, "page[size]": 10 })

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/instruments", {
      "page[number]": 0,
      "page[size]": 10,
    })
  })
})

describe("getAllInstruments", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches all instruments", async () => {
    const mockResponse: JsonApiListResponse<InstrumentResource> = { data: [mockInstrument] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getAllInstruments("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/instruments/all")
    expect(result).toHaveLength(1)
  })
})

describe("getInstrument", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches a single instrument", async () => {
    const mockResponse: JsonApiResponse<InstrumentResource> = { data: mockInstrument }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getInstrument("my-ledger", "instr-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/instruments/instr-1")
    expect(result.data.id).toBe("instr-1")
  })
})

describe("createInstrument", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates an instrument", async () => {
    const mockResponse: JsonApiResponse<InstrumentResource> = { data: mockInstrument }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const input = { symbol: "PETR4", name: "Petrobras PN", type: "STOCK" as const, currency: "BRL" }
    const result = await createInstrument("my-ledger", input)

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/my-ledger/instruments", input)
    expect(result.data.attributes.symbol).toBe("PETR4")
  })
})

describe("updateInstrument", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("updates an instrument", async () => {
    const mockResponse: JsonApiResponse<InstrumentResource> = { data: mockInstrument }
    vi.mocked(apiClient.put).mockResolvedValueOnce(mockResponse)

    const input = {
      symbol: "PETR4",
      name: "Updated Name",
      type: "STOCK" as const,
      currency: "BRL",
      status: "ACTIVE" as const,
    }
    await updateInstrument("my-ledger", "instr-1", input)

    expect(apiClient.put).toHaveBeenCalledWith("/ledgers/my-ledger/instruments/instr-1", input)
  })
})

describe("deleteInstrument", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes an instrument", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteInstrument("my-ledger", "instr-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/ledgers/my-ledger/instruments/instr-1")
  })
})

describe("getInstrumentPrices", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches instrument prices", async () => {
    const mockResponse: JsonApiListResponse<InstrumentPriceResource> = { data: [mockPrice] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getInstrumentPrices("my-ledger", { instrumentId: "instr-1" })

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/instrument-prices", {
      instrumentId: "instr-1",
    })
    expect(result.data).toHaveLength(1)
  })
})

describe("createInstrumentPrice", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates an instrument price", async () => {
    const mockResponse: JsonApiResponse<InstrumentPriceResource> = { data: mockPrice }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const input = { instrumentId: "instr-1", price: 35.5, effectiveDate: "2026-01-15" }
    const result = await createInstrumentPrice("my-ledger", input)

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/my-ledger/instrument-prices", input)
    expect(result.data.attributes.price).toBe(35.5)
  })
})

describe("deleteInstrumentPrice", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes an instrument price", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteInstrumentPrice("my-ledger", "price-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/ledgers/my-ledger/instrument-prices/price-1")
  })
})

describe("getPortfolio", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches portfolio positions", async () => {
    const mockResponse: JsonApiListResponse<InstrumentPositionResource> = { data: [mockPosition] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getPortfolio("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/portfolio")
    expect(result.data).toHaveLength(1)
  })
})

describe("getAccountPortfolio", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches account-specific portfolio", async () => {
    const mockResponse: JsonApiListResponse<InstrumentPositionResource> = { data: [mockPosition] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getAccountPortfolio("my-ledger", "acc-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/portfolio/accounts/acc-1")
    expect(result.data).toHaveLength(1)
  })
})
