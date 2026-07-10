import type { AccountResource, LedgerResource } from "@/api/types"

// Override shape for a JSON:API resource factory: top-level fields replace,
// attributes merge onto the defaults (two-level merge).
type ResourceOverrides<T extends { attributes: object }> = Omit<Partial<T>, "attributes"> & {
  attributes?: Partial<T["attributes"]>
}

function mergeResource<T extends { attributes: object }>(base: T, overrides?: ResourceOverrides<T>): T {
  return {
    ...base,
    ...overrides,
    attributes: { ...base.attributes, ...overrides?.attributes },
  }
}

export function makeLedger(overrides?: ResourceOverrides<LedgerResource>): LedgerResource {
  const base: LedgerResource = {
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
  return mergeResource(base, overrides)
}

export function makeAccount(overrides?: ResourceOverrides<AccountResource>): AccountResource {
  const base: AccountResource = {
    type: "accounts",
    id: "account-1",
    attributes: {
      slug: "checking-account",
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
  return mergeResource(base, overrides)
}
