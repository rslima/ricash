import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, LedgerResource, PaginationParams } from "./types"

export async function getLedgers(params?: PaginationParams): Promise<JsonApiListResponse<LedgerResource>> {
  return apiClient.get("/ledgers", params as Record<string, string | number | undefined>)
}

export async function getLedger(id: string): Promise<JsonApiResponse<LedgerResource>> {
  return apiClient.get(`/ledgers/${id}`)
}

export interface CreateLedgerData {
  data: {
    type: "ledgers"
    attributes: {
      name: string
      description?: string
      currency: string
    }
  }
}

export async function createLedger(data: CreateLedgerData): Promise<JsonApiResponse<LedgerResource>> {
  return apiClient.post("/ledgers", data)
}

export interface UpdateLedgerData {
  data: {
    type: "ledgers"
    id: string
    attributes: {
      name?: string
      description?: string
    }
  }
}

export async function updateLedger(id: string, data: UpdateLedgerData): Promise<JsonApiResponse<LedgerResource>> {
  return apiClient.patch(`/ledgers/${id}`, data)
}

export async function deleteLedger(id: string): Promise<void> {
  return apiClient.delete(`/ledgers/${id}`)
}
