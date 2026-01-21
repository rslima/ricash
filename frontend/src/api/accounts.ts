import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, AccountResource, PaginationParams } from "./types"

export async function getAccounts(
  ledgerId: string,
  params?: PaginationParams
): Promise<JsonApiListResponse<AccountResource>> {
  return apiClient.get(`/ledgers/${ledgerId}/accounts`, params as Record<string, string | number | undefined>)
}

export async function getAccount(ledgerId: string, accountId: string): Promise<JsonApiResponse<AccountResource>> {
  return apiClient.get(`/ledgers/${ledgerId}/accounts/${accountId}`)
}

export interface CreateAccountData {
  data: {
    type: "accounts"
    attributes: {
      name: string
      type: "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"
      currency: string
      description?: string
      parentAccountId?: string
    }
  }
}

export async function createAccount(
  ledgerId: string,
  data: CreateAccountData
): Promise<JsonApiResponse<AccountResource>> {
  return apiClient.post(`/ledgers/${ledgerId}/accounts`, data)
}

export interface UpdateAccountData {
  data: {
    type: "accounts"
    id: string
    attributes: {
      name?: string
      description?: string
    }
  }
}

export async function updateAccount(
  ledgerId: string,
  accountId: string,
  data: UpdateAccountData
): Promise<JsonApiResponse<AccountResource>> {
  return apiClient.patch(`/ledgers/${ledgerId}/accounts/${accountId}`, data)
}

export async function deleteAccount(ledgerId: string, accountId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerId}/accounts/${accountId}`)
}
