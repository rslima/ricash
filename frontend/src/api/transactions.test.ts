import { describe, it, expect, vi, beforeEach } from "vitest"
import {
  getTransactions,
  getTransaction,
  createTransaction,
  updateTransaction,
  deleteTransaction,
  getTransactionDescriptions,
  getTransactionTemplates,
} from "./transactions"
import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, TransactionResource } from "./types"

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockTransaction: TransactionResource = {
  type: "transactions",
  id: "txn-1",
  attributes: {
    date: "2026-01-15",
    description: "Groceries",
    amount: 50.0,
    currency: "USD",
    entries: [
      {
        accountId: "acc-1",
        accountName: "Checking",
        amount: 50.0,
        currency: "USD",
        type: "DEBIT",
      },
      {
        accountId: "acc-2",
        accountName: "Expenses",
        amount: 50.0,
        currency: "USD",
        type: "CREDIT",
      },
    ],
    createdAt: "2026-01-15T00:00:00Z",
    updatedAt: "2026-01-15T00:00:00Z",
  },
}

describe("getTransactions", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches transactions without filters", async () => {
    const mockResponse: JsonApiListResponse<TransactionResource> = { data: [mockTransaction] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getTransactions("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/transactions", undefined)
    expect(result.data).toHaveLength(1)
  })

  it("fetches transactions with filters", async () => {
    const mockResponse: JsonApiListResponse<TransactionResource> = { data: [mockTransaction] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    await getTransactions("my-ledger", { accountId: "acc-1", "page[number]": 0, "page[size]": 10 })

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/transactions", {
      accountId: "acc-1",
      "page[number]": 0,
      "page[size]": 10,
    })
  })
})

describe("getTransaction", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches a single transaction", async () => {
    const mockResponse: JsonApiResponse<TransactionResource> = { data: mockTransaction }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getTransaction("my-ledger", "txn-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/transactions/txn-1")
    expect(result.data.id).toBe("txn-1")
  })
})

describe("createTransaction", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates a transaction", async () => {
    const mockResponse: JsonApiResponse<TransactionResource> = { data: mockTransaction }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const data = {
      date: "2026-01-15",
      description: "Groceries",
      entries: [
        { accountId: "acc-1", amount: 50, currency: "USD", type: "DEBIT" as const },
        { accountId: "acc-2", amount: 50, currency: "USD", type: "CREDIT" as const },
      ],
    }

    const result = await createTransaction("my-ledger", data)

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/my-ledger/transactions", data)
    expect(result.data.attributes.description).toBe("Groceries")
  })
})

describe("updateTransaction", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("updates a transaction", async () => {
    const mockResponse: JsonApiResponse<TransactionResource> = { data: mockTransaction }
    vi.mocked(apiClient.put).mockResolvedValueOnce(mockResponse)

    const data = {
      date: "2026-01-15",
      description: "Updated Groceries",
      entries: [
        { accountId: "acc-1", amount: 60, currency: "USD", type: "DEBIT" as const },
        { accountId: "acc-2", amount: 60, currency: "USD", type: "CREDIT" as const },
      ],
    }

    await updateTransaction("my-ledger", "txn-1", data)

    expect(apiClient.put).toHaveBeenCalledWith("/ledgers/my-ledger/transactions/txn-1", data)
  })
})

describe("deleteTransaction", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes a transaction", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteTransaction("my-ledger", "txn-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/ledgers/my-ledger/transactions/txn-1")
  })
})

describe("getTransactionDescriptions", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches distinct descriptions", async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce(["Groceries", "Rent"])

    const result = await getTransactionDescriptions("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/transactions/descriptions")
    expect(result).toEqual(["Groceries", "Rent"])
  })
})

describe("getTransactionTemplates", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches transaction templates", async () => {
    const mockResponse: JsonApiListResponse<TransactionResource> = { data: [mockTransaction] }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getTransactionTemplates("my-ledger")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/my-ledger/transactions/templates")
    expect(result).toHaveLength(1)
  })
})
