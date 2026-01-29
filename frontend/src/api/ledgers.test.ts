import { describe, it, expect, vi, beforeEach } from "vitest"
import { getLedgers, getLedger, createLedger, updateLedger, deleteLedger } from "./ledgers"
import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, LedgerResource } from "./types"

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

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

describe("getLedgers", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches ledgers without pagination params", async () => {
    const mockResponse: JsonApiListResponse<LedgerResource> = {
      data: [mockLedger],
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getLedgers()

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers", undefined)
    expect(result.data).toHaveLength(1)
    expect(result.data[0].attributes.name).toBe("Personal Finance")
  })

  it("fetches ledgers with pagination params", async () => {
    const mockResponse: JsonApiListResponse<LedgerResource> = {
      data: [mockLedger],
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    await getLedgers({ "page[number]": 0, "page[size]": 10 })

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers", {
      "page[number]": 0,
      "page[size]": 10,
    })
  })
})

describe("getLedger", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches a single ledger by id", async () => {
    const mockResponse: JsonApiResponse<LedgerResource> = {
      data: mockLedger,
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getLedger("ledger-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/ledger-1")
    expect(result.data.id).toBe("ledger-1")
  })
})

describe("createLedger", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates a ledger with required fields", async () => {
    const mockResponse: JsonApiResponse<LedgerResource> = {
      data: mockLedger,
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const result = await createLedger({
      name: "Personal Finance",
      currency: "USD",
    })

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers", {
      name: "Personal Finance",
      currency: "USD",
    })
    expect(result.data.attributes.name).toBe("Personal Finance")
  })

  it("creates a ledger with description", async () => {
    const mockResponse: JsonApiResponse<LedgerResource> = {
      data: mockLedger,
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    await createLedger({
      name: "Personal Finance",
      description: "My personal ledger",
      currency: "USD",
    })

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers", {
      name: "Personal Finance",
      description: "My personal ledger",
      currency: "USD",
    })
  })
})

describe("updateLedger", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("updates a ledger", async () => {
    const mockResponse: JsonApiResponse<LedgerResource> = {
      data: mockLedger,
    }
    vi.mocked(apiClient.put).mockResolvedValueOnce(mockResponse)

    const updateData = {
      name: "Updated Name",
      description: "Updated description",
    }

    await updateLedger("personal-finance", updateData)

    expect(apiClient.put).toHaveBeenCalledWith("/ledgers/personal-finance", updateData)
  })
})

describe("deleteLedger", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes a ledger", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteLedger("ledger-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/ledgers/ledger-1")
  })
})
