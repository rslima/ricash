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

export type JsonApiListResponse<T> = JsonApiResponse<T[]>

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
  currency: string
  toAmount?: number
  toCurrency?: string
  type: "DEBIT" | "CREDIT"
  instrumentId?: string
  quantity?: number
  instrumentSymbol?: string
  envelopeId?: string
}

export type TransactionResource = JsonApiResource<"transactions", TransactionAttributes>

export interface ExchangeRateAttributes {
  fromCurrency: string
  toCurrency: string
  rate: number
  effectiveDate: string
  source: string
  createdAt: string
}

export type ExchangeRateResource = JsonApiResource<"exchange-rates", ExchangeRateAttributes>

// Instrument types

export type InstrumentType = "STOCK" | "ETF" | "TREASURY_BOND" | "FIXED_INCOME" | "FUND"
export type InstrumentStatus = "ACTIVE" | "INACTIVE"

export interface InstrumentAttributes {
  ledgerId: string
  symbol: string
  name: string
  type: InstrumentType
  currency: string
  market?: string
  isin?: string
  status: InstrumentStatus
  createdAt: string
}

export type InstrumentResource = JsonApiResource<"instruments", InstrumentAttributes>

export interface InstrumentPriceAttributes {
  instrumentId: string
  instrumentSymbol?: string
  price: number
  effectiveDate: string
  source: string
  createdAt: string
}

export type InstrumentPriceResource = JsonApiResource<"instrument-prices", InstrumentPriceAttributes>

export interface InstrumentPositionAttributes {
  instrumentId: string
  instrumentSymbol: string
  instrumentName: string
  instrumentType: InstrumentType
  currency: string
  quantity: number
  totalCost: number
  averageCost: number
  currentPrice?: number
  currentValue?: number
  unrealizedGain?: number
  unrealizedGainPercent?: number
}

export type InstrumentPositionResource = JsonApiResource<"positions", InstrumentPositionAttributes>

// Envelope types

export type EnvelopeType = "EXPENSE" | "INCOME"
export type EnvelopeStatus = "ACTIVE" | "ARCHIVED"

export interface EnvelopeAttributes {
  name: string
  description: string | null
  currency: string
  type: EnvelopeType
  status: EnvelopeStatus
  parentEnvelopeId: string | null
  createdAt: string
}

export type EnvelopeResource = JsonApiResource<"envelopes", EnvelopeAttributes>

export interface EnvelopeAllocationAttributes {
  envelopeId: string
  periodYear: number
  periodMonth: number
  allocatedAmount: number
  notes: string | null
  createdAt: string
  updatedAt: string
}

export type EnvelopeAllocationResource = JsonApiResource<"envelope-allocations", EnvelopeAllocationAttributes>

// Plain JSON types for budget (not JSON:API)
export interface EnvelopeBalance {
  envelopeId: string
  periodYear: number
  periodMonth: number
  rollover: number
  allocated: number
  spent: number
  available: number
}

export interface BudgetSummary {
  id: string
  periodYear: number
  periodMonth: number
  toBeBudgeted: number
  envelopeBalances: EnvelopeBalance[]
}

// Plain JSON types for account reports (not JSON:API)
export interface BalanceSummary {
  id: string
  balanceByCurrency: Record<string, number>
}

// Pagination params
export interface PaginationParams {
  "page[number]"?: number
  "page[size]"?: number
}
