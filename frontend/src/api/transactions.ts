import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, TransactionResource, PaginationParams } from "./types"

export interface TransactionFilters extends PaginationParams {
  accountId?: string
}

export async function getTransactions(
  ledgerSlug: string,
  params?: TransactionFilters
): Promise<JsonApiListResponse<TransactionResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions`, params as Record<string, string | number | undefined>)
}

export async function getTransaction(
  ledgerSlug: string,
  transactionId: string
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions/${transactionId}`)
}

export interface TransactionEntryInput {
  accountId: string
  amount: number
  currency: string
  toAmount?: number
  toCurrency?: string
  type: "DEBIT" | "CREDIT"
  instrumentId?: string
  quantity?: number
}

export interface CreateTransactionData {
  date: string
  description: string
  entries: TransactionEntryInput[]
}

export async function createTransaction(
  ledgerSlug: string,
  data: CreateTransactionData
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.post(`/ledgers/${ledgerSlug}/transactions`, data)
}

export interface UpdateTransactionData {
  date: string
  description: string
  entries: TransactionEntryInput[]
}

export async function updateTransaction(
  ledgerSlug: string,
  transactionId: string,
  data: UpdateTransactionData
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.put(`/ledgers/${ledgerSlug}/transactions/${transactionId}`, data)
}

export async function deleteTransaction(ledgerSlug: string, transactionId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerSlug}/transactions/${transactionId}`)
}
