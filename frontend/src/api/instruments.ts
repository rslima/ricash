import { apiClient } from "./client"
import type {
  InstrumentResource,
  InstrumentPriceResource,
  InstrumentPositionResource,
  InstrumentType,
  InstrumentStatus,
  JsonApiListResponse,
  JsonApiResponse,
  PaginationParams
} from "./types"

export interface CreateInstrumentInput {
  symbol: string
  name: string
  type: InstrumentType
  currency: string
  market?: string
  isin?: string
}

export interface UpdateInstrumentInput extends CreateInstrumentInput {
  status: InstrumentStatus
}

export interface CreateInstrumentPriceInput {
  instrumentId: string
  price: number
  effectiveDate: string
}

// Instruments API

export async function getInstruments(
  ledgerSlug: string,
  params?: PaginationParams
): Promise<JsonApiListResponse<InstrumentResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/instruments`, params as Record<string, string | number | undefined>)
}

export async function getAllInstruments(ledgerSlug: string): Promise<InstrumentResource[]> {
  const response: JsonApiListResponse<InstrumentResource> = await apiClient.get(`/ledgers/${ledgerSlug}/instruments/all`)
  return response.data
}

export async function getInstrument(
  ledgerSlug: string,
  instrumentId: string
): Promise<JsonApiResponse<InstrumentResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/instruments/${instrumentId}`)
}

export async function createInstrument(
  ledgerSlug: string,
  input: CreateInstrumentInput
): Promise<JsonApiResponse<InstrumentResource>> {
  return apiClient.post(`/ledgers/${ledgerSlug}/instruments`, input)
}

export async function updateInstrument(
  ledgerSlug: string,
  instrumentId: string,
  input: UpdateInstrumentInput
): Promise<JsonApiResponse<InstrumentResource>> {
  return apiClient.put(`/ledgers/${ledgerSlug}/instruments/${instrumentId}`, input)
}

export async function deleteInstrument(ledgerSlug: string, instrumentId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerSlug}/instruments/${instrumentId}`)
}

// Instrument Prices API

export async function getInstrumentPrices(
  ledgerSlug: string,
  params?: PaginationParams & { instrumentId?: string }
): Promise<JsonApiListResponse<InstrumentPriceResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/instrument-prices`, params as Record<string, string | number | undefined>)
}

export async function createInstrumentPrice(
  ledgerSlug: string,
  input: CreateInstrumentPriceInput
): Promise<JsonApiResponse<InstrumentPriceResource>> {
  return apiClient.post(`/ledgers/${ledgerSlug}/instrument-prices`, input)
}

export async function deleteInstrumentPrice(ledgerSlug: string, priceId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerSlug}/instrument-prices/${priceId}`)
}

// Portfolio API

export async function getPortfolio(ledgerSlug: string): Promise<JsonApiListResponse<InstrumentPositionResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/portfolio`)
}

export async function getAccountPortfolio(
  ledgerSlug: string,
  accountId: string
): Promise<JsonApiListResponse<InstrumentPositionResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/portfolio/accounts/${accountId}`)
}
