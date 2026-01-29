// JSON:API standard types

export interface JsonApiResource<T extends string, A> {
  type: T
  id: string
  attributes: A
  links?: {
    self?: string
  }
}

export interface JsonApiResponse<T> {
  data: T
  links?: {
    self?: string
    first?: string
    last?: string
    prev?: string | null
    next?: string | null
  }
  meta?: {
    page?: {
      number: number
      size: number
      totalElements: number
      totalPages: number
    }
  }
}

export interface JsonApiListResponse<T> extends JsonApiResponse<T[]> {}

// Domain types

export interface UserAttributes {
  username: string
  email: string
  firstName: string
  lastName: string
  roles: string[]
  createdAt: string
  updatedAt: string
}

export type UserResource = JsonApiResource<"users", UserAttributes>

export interface LedgerAttributes {
  slug: string
  name: string
  description: string | null
  currency: string
  createdAt: string
  updatedAt: string
}

export type LedgerResource = JsonApiResource<"ledgers", LedgerAttributes>

export interface AccountAttributes {
  slug: string
  name: string
  type: "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"
  currency: string
  balance: number
  description: string | null
  parentAccountId: string | null
  createdAt: string
  updatedAt: string
}

export type AccountResource = JsonApiResource<"accounts", AccountAttributes>

export interface TransactionAttributes {
  date: string
  description: string
  amount: number
  currency: string
  entries: TransactionEntry[]
  createdAt: string
  updatedAt: string
}

export interface TransactionEntry {
  accountId: string
  accountName: string
  amount: number
  type: "DEBIT" | "CREDIT"
}

export type TransactionResource = JsonApiResource<"transactions", TransactionAttributes>

// Pagination params
export interface PaginationParams {
  "page[number]"?: number
  "page[size]"?: number
}
