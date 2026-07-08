import { apiClient } from "./client"
import type {
  JsonApiListResponse,
  JsonApiResponse,
  EnvelopeResource,
  EnvelopeAllocationResource,
  EnvelopeBalance,
  BudgetSummary,
  PaginationParams,
  EnvelopeType,
  EnvelopeStatus,
} from "./types"

export async function getEnvelopes(
  ledgerSlug: string,
  params?: PaginationParams
): Promise<JsonApiListResponse<EnvelopeResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/envelopes`, params as Record<string, string | number | undefined>)
}

export async function getEnvelope(
  ledgerSlug: string,
  envelopeId: string
): Promise<JsonApiResponse<EnvelopeResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}`)
}

export interface CreateEnvelopeData {
  name: string
  description?: string
  currency: string
  type: EnvelopeType
  parentEnvelopeId?: string
}

export async function createEnvelope(
  ledgerSlug: string,
  data: CreateEnvelopeData
): Promise<JsonApiResponse<EnvelopeResource>> {
  return apiClient.post(`/ledgers/${ledgerSlug}/envelopes`, data)
}

export interface UpdateEnvelopeData {
  name: string
  description?: string
  currency: string
  type: EnvelopeType
  status: EnvelopeStatus
  parentEnvelopeId?: string | null
}

export async function updateEnvelope(
  ledgerSlug: string,
  envelopeId: string,
  data: UpdateEnvelopeData
): Promise<JsonApiResponse<EnvelopeResource>> {
  return apiClient.put(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}`, data)
}

export async function deleteEnvelope(ledgerSlug: string, envelopeId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}`)
}

export interface AllocateEnvelopeData {
  year: number
  month: number
  allocatedAmount: number
  notes?: string
}

export async function allocateEnvelope(
  ledgerSlug: string,
  envelopeId: string,
  data: AllocateEnvelopeData
): Promise<JsonApiResponse<EnvelopeAllocationResource>> {
  return apiClient.post(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}/allocations`, data)
}

export async function getEnvelopeBalance(
  ledgerSlug: string,
  envelopeId: string,
  year: number,
  month: number
): Promise<EnvelopeBalance> {
  return apiClient.get(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}/balance`, { year, month })
}

export async function getEnvelopeAccounts(
  ledgerSlug: string,
  envelopeId: string
): Promise<{ accountIds: string[] }> {
  return apiClient.get(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}/accounts`)
}

export async function setEnvelopeAccounts(
  ledgerSlug: string,
  envelopeId: string,
  accountIds: string[]
): Promise<{ accountIds: string[] }> {
  return apiClient.put(`/ledgers/${ledgerSlug}/envelopes/${envelopeId}/accounts`, accountIds)
}

export async function getBudgetSummary(
  ledgerSlug: string,
  year: number,
  month: number
): Promise<BudgetSummary> {
  return apiClient.get(`/ledgers/${ledgerSlug}/budget`, { year, month })
}

export async function getEnvelopeMappings(
  ledgerSlug: string
): Promise<Record<string, string>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/envelope-mappings`)
}
