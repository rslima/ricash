import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, TransactionResource, PaginationParams } from "./types"

export interface TransactionFilters extends PaginationParams {
  accountId?: string
}

export async function getTransactions(
  ledgerSlug: string,
  params?: TransactionFilters
): Promise<JsonApiListResponse<TransactionResource>> {
  return apiClient.get(`/ledgers/${ledgerId}/transactions`, params as Record<string, string | number | undefined>)
}

export async function getTransaction(
  ledgerSlug: string,
  transactionId: string
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.get(`/ledgers/${ledgerId}/transactions/${transactionId}`)
}

export interface TransactionEntryInput {
  accountId: string
  amount: number
  type: "DEBIT" | "CREDIT"
}

export interface CreateTransactionData {
  date: string
  description: string
  entries: TransactionEntryInput[]
}

export async function createTransaction(
  ledgerId: string,
  data: CreateTransactionData
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.post(`/ledgers/${ledgerId}/transactions`, data)
}

export interface UpdateTransactionData {
  date: string
  description: string
  entries: TransactionEntryInput[]
}

export async function updateTransaction(
  ledgerId: string,
  transactionId: string,
  data: UpdateTransactionData
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.patch(`/ledgers/${ledgerId}/transactions/${transactionId}`, data)
}

export async function deleteTransaction(ledgerId: string, transactionId: string): Promise<void> {
  return apiClient.delete(`/ledgers/${ledgerId}/transactions/${transactionId}`)
}
