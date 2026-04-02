import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, AccountResource, BalanceSummary, PaginationParams } from "./types"

export async function getAccounts(
  ledgerSlug: string,
  params?: PaginationParams
): Promise<JsonApiListResponse<AccountResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/accounts`, params as Record<string, string | number | undefined>)
}

export async function getAccount(ledgerSlug: string, accountId: string): Promise<JsonApiResponse<AccountResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/accounts/${accountId}`)
}

export interface CreateAccountData {
  name: string
  type: "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"
  currency: string
  description?: string
  parentAccountId?: string
}

export async function createAccount(
  ledgerSlug: string,
  data: CreateAccountData
): Promise<JsonApiResponse<AccountResource>> {
  return apiClient.post(`/ledgers/${ledgerSlug}/accounts`, data)
}

export interface UpdateAccountData {
  name: string
  description?: string
  type: "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"
  currency: string
  parentAccountId?: string | null
}

export async function updateAccount(
  ledgerSlug: string,
  accountId: string,
  data: UpdateAccountData
): Promise<JsonApiResponse<AccountResource>> {
  return apiClient.put(`/ledgers/${ledgerSlug}/accounts/${accountId}`, data)
}

export async function deleteAccount(ledgerSlug: string, accountId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerSlug}/accounts/${accountId}`)
}

export async function getBalanceSummary(ledgerSlug: string): Promise<BalanceSummary> {
  return apiClient.get(`/ledgers/${ledgerSlug}/accounts/balance-summary`)
}
