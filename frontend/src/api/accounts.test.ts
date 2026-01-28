import { describe, it, expect, vi, beforeEach } from "vitest"
import { getAccounts, getAccount, createAccount, updateAccount, deleteAccount } from "./accounts"
import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, AccountResource } from "./types"

vi.mock("./client", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockAccount: AccountResource = {
  type: "accounts",
  id: "account-1",
  attributes: {
    name: "Checking Account",
    type: "ASSET",
    currency: "USD",
    balance: 1000,
    description: "Main checking account",
    parentAccountId: null,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
  },
}

describe("getAccounts", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches accounts for a ledger", async () => {
    const mockResponse: JsonApiListResponse<AccountResource> = {
      data: [mockAccount],
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getAccounts("ledger-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/ledger-1/accounts", undefined)
    expect(result.data).toHaveLength(1)
    expect(result.data[0].attributes.name).toBe("Checking Account")
  })

  it("fetches accounts with pagination params", async () => {
    const mockResponse: JsonApiListResponse<AccountResource> = {
      data: [mockAccount],
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    await getAccounts("ledger-1", { "page[number]": 0, "page[size]": 10 })

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/ledger-1/accounts", {
      "page[number]": 0,
      "page[size]": 10,
    })
  })
})

describe("getAccount", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("fetches a single account by id", async () => {
    const mockResponse: JsonApiResponse<AccountResource> = {
      data: mockAccount,
    }
    vi.mocked(apiClient.get).mockResolvedValueOnce(mockResponse)

    const result = await getAccount("ledger-1", "account-1")

    expect(apiClient.get).toHaveBeenCalledWith("/ledgers/ledger-1/accounts/account-1")
    expect(result.data.id).toBe("account-1")
  })
})

describe("createAccount", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("creates an account with required fields", async () => {
    const mockResponse: JsonApiResponse<AccountResource> = {
      data: mockAccount,
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    const result = await createAccount("ledger-1", {
      name: "Checking Account",
      type: "ASSET",
      currency: "USD",
    })

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/ledger-1/accounts", {
      name: "Checking Account",
      type: "ASSET",
      currency: "USD",
    })
    expect(result.data.attributes.name).toBe("Checking Account")
  })

  it("creates an account with description", async () => {
    const mockResponse: JsonApiResponse<AccountResource> = {
      data: mockAccount,
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    await createAccount("ledger-1", {
      name: "Checking Account",
      type: "ASSET",
      currency: "USD",
      description: "Main checking account",
    })

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/ledger-1/accounts", {
      name: "Checking Account",
      type: "ASSET",
      currency: "USD",
      description: "Main checking account",
    })
  })

  it("creates an account with parent account", async () => {
    const mockResponse: JsonApiResponse<AccountResource> = {
      data: mockAccount,
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce(mockResponse)

    await createAccount("ledger-1", {
      name: "Sub Account",
      type: "ASSET",
      currency: "USD",
      parentAccountId: "parent-1",
    })

    expect(apiClient.post).toHaveBeenCalledWith("/ledgers/ledger-1/accounts", {
      name: "Sub Account",
      type: "ASSET",
      currency: "USD",
      parentAccountId: "parent-1",
    })
  })

  it("creates accounts of different types", async () => {
    const types = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"] as const

    for (const type of types) {
      vi.mocked(apiClient.post).mockResolvedValueOnce({
        data: { ...mockAccount, attributes: { ...mockAccount.attributes, type } },
      })

      await createAccount("ledger-1", {
        name: `${type} Account`,
        type,
        currency: "USD",
      })

      expect(apiClient.post).toHaveBeenLastCalledWith("/ledgers/ledger-1/accounts", {
        name: `${type} Account`,
        type,
        currency: "USD",
      })
    }
  })
})

describe("updateAccount", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("updates an account", async () => {
    const mockResponse: JsonApiResponse<AccountResource> = {
      data: mockAccount,
    }
    vi.mocked(apiClient.patch).mockResolvedValueOnce(mockResponse)

    const updateData = {
      data: {
        type: "accounts" as const,
        id: "account-1",
        attributes: {
          name: "Updated Name",
        },
      },
    }

    await updateAccount("ledger-1", "account-1", updateData)

    expect(apiClient.patch).toHaveBeenCalledWith(
      "/ledgers/ledger-1/accounts/account-1",
      updateData
    )
  })
})

describe("deleteAccount", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("deletes an account", async () => {
    vi.mocked(apiClient.delete).mockResolvedValueOnce(undefined)

    await deleteAccount("ledger-1", "account-1")

    expect(apiClient.delete).toHaveBeenCalledWith("/ledgers/ledger-1/accounts/account-1")
  })
})
