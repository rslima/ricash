import { describe, it, expect, vi, beforeEach } from "vitest"
import {
  getEnvelopes,
  getEnvelope,
  createEnvelope,
  updateEnvelope,
  deleteEnvelope,
  allocateEnvelope,
  getEnvelopeBalance,
  getEnvelopeAccounts,
  setEnvelopeAccounts,
  getBudgetSummary,
  getEnvelopeMappings,
} from "./envelopes"
import { apiClient } from "./client"
import type {
  JsonApiListResponse,
  JsonApiResponse,
  EnvelopeResource,
  EnvelopeAllocationResource,
  EnvelopeBalance,
  BudgetSummary,
} from "./types"

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockEnvelope: EnvelopeResource = {
  type: "envelopes",
  id: "env-1",
  attributes: {
    name: "Groceries",
    description: "Food budget",
    currency: "USD",
    type: "EXPENSE",
    status: "ACTIVE",
    parentEnvelopeId: null,
    createdAt: "2026-01-01T00:00:00Z",
  },
}

describe("getEnvelopes", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches envelopes without pagination", async () => {
    const mockResponse: JsonApiListResponse<EnvelopeResource> = { data: [mockEnvelope] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getEnvelopes("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes", undefined)
    expect(result.data).toHaveLength(1)
  })

  it("fetches envelopes with pagination", async () => {
    const mockResponse: JsonApiListResponse<EnvelopeResource> = { data: [mockEnvelope] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    await getEnvelopes("my-ledger", { "page[number]": 0, "page[size]": 10 })

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes", {
      "page[number]": 0,
      "page[size]": 10,
    })
  })
})

describe("getEnvelope", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches a single envelope", async () => {
    const mockResponse: JsonApiResponse<EnvelopeResource> = { data: mockEnvelope }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getEnvelope("my-ledger", "env-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1")
    expect(result.data.id).toBe("env-1")
  })
})

describe("createEnvelope", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates an envelope", async () => {
    const mockResponse: JsonApiResponse<EnvelopeResource> = { data: mockEnvelope }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const data = { name: "Groceries", currency: "USD", type: "EXPENSE" as const }
    const result = await createEnvelope("my-ledger", data)

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes", data)
    expect(result.data.attributes.name).toBe("Groceries")
  })
})

describe("updateEnvelope", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("updates an envelope", async () => {
    const mockResponse: JsonApiResponse<EnvelopeResource> = { data: mockEnvelope }
    vi.mocked(apiClient.put).mockResolvedValueOnce(mockResponse)

    const data = {
      name: "Updated",
      currency: "USD",
      type: "EXPENSE" as const,
      status: "ACTIVE" as const,
    }
    await updateEnvelope("my-ledger", "env-1", data)

    expect(apiClient.put).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1", data)
  })
})

describe("deleteEnvelope", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes an envelope", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteEnvelope("my-ledger", "env-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1")
  })
})

describe("allocateEnvelope", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("allocates to an envelope", async () => {
    const mockAllocation: JsonApiResponse<EnvelopeAllocationResource> = {
      data: {
        type: "envelope-allocations",
        id: "alloc-1",
        attributes: {
          envelopeId: "env-1",
          periodYear: 2026,
          periodMonth: 3,
          allocatedAmount: 500,
          notes: null,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        },
      },
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockAllocation)

    const data = { year: 2026, month: 3, allocatedAmount: 500 }
    const result = await allocateEnvelope("my-ledger", "env-1", data)

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1/allocations", data)
    expect(result.data.attributes.allocatedAmount).toBe(500)
  })
})

describe("getEnvelopeBalance", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches envelope balance", async () => {
    const mockBalance: EnvelopeBalance = {
      envelopeId: "env-1",
      periodYear: 2026,
      periodMonth: 3,
      rollover: 100,
      allocated: 500,
      spent: 200,
      available: 400,
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockBalance)

    const result = await getEnvelopeBalance("my-ledger", "env-1", 2026, 3)

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1/balance", {
      year: 2026,
      month: 3,
    })
    expect(result.available).toBe(400)
  })
})

describe("getEnvelopeAccounts", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches envelope account mappings", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ accountIds: ["acc-1", "acc-2"] })

    const result = await getEnvelopeAccounts("my-ledger", "env-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1/accounts")
    expect(result.accountIds).toEqual(["acc-1", "acc-2"])
  })
})

describe("setEnvelopeAccounts", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("sets envelope account mappings", async () => {
    vi.mocked(apiClient.put).mockResolvedValueOnce({ accountIds: ["acc-1"] })

    const result = await setEnvelopeAccounts("my-ledger", "env-1", ["acc-1"])

    expect(apiClient.put).toHaveBeenCalledWith("/ledgers/my-ledger/envelopes/env-1/accounts", ["acc-1"])
    expect(result.accountIds).toEqual(["acc-1"])
  })
})

describe("getBudgetSummary", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches budget summary", async () => {
    const mockSummary: BudgetSummary = {
      id: "budget-1",
      periodYear: 2026,
      periodMonth: 3,
      toBeBudgeted: 2000,
      envelopeBalances: [],
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockSummary)

    const result = await getBudgetSummary("my-ledger", 2026, 3)

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/budget", { year: 2026, month: 3 })
    expect(result.toBeBudgeted).toBe(2000)
  })
})

describe("getEnvelopeMappings", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches all envelope mappings", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ "acc-1": "env-1" })

    const result = await getEnvelopeMappings("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/envelope-mappings")
    expect(result).toEqual({ "acc-1": "env-1" })
  })
})
