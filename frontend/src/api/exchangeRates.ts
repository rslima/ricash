import { apiClient } from "./client"
import type { ExchangeRateResource, JsonApiListResponse, JsonApiResponse, PaginationParams } from "./types"

export interface CreateExchangeRateInput {
  fromCurrency: string
  toCurrency: string
  rate: number
  effectiveDate: string
}

export async function getExchangeRates(params?: PaginationParams): Promise<JsonApiListResponse<ExchangeRateResource>> {
  return apiClient.get("/exchange-rates", params as Record<string, string | number | undefined>)
}

export async function createExchangeRate(input: CreateExchangeRateInput): Promise<JsonApiResponse<ExchangeRateResource>> {
  return apiClient.post("/exchange-rates", {
    fromCurrency: input.fromCurrency,
    toCurrency: input.toCurrency,
    rate: input.rate,
    effectiveDate: input.effectiveDate
  })
}

export async function deleteExchangeRate(id: string): Promise<void> {
  return apiClient.delete(`/exchange-rates/${id}`)
}
