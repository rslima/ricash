import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, TransactionResource, PaginationParams } from "./types"

export async function getTransactions(
  ledgerId: string,
  params?: PaginationParams
): Promise<JsonApiListResponse<TransactionResource>> {
  return apiClient.get(`/ledgers/${ledgerId}/transactions`, params as Record<string, string | number | undefined>)
}

export async function getTransaction(
  ledgerId: string,
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
  data: {
    type: "transactions"
    attributes: {
      date: string
      description: string
      entries: TransactionEntryInput[]
    }
  }
}

export async function createTransaction(
  ledgerId: string,
  data: CreateTransactionData
): Promise<JsonApiResponse<TransactionResource>> {
  return apiClient.post(`/ledgers/${ledgerId}/transactions`, data)
}

export interface UpdateTransactionData {
  data: {
    type: "transactions"
    id: string
    attributes: {
      date?: string
      description?: string
      entries?: TransactionEntryInput[]
    }
  }
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
