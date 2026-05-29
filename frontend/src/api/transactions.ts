import { apiClient } from "./client"
import type { JsonApiListResponse, JsonApiResponse, TransactionResource, PaginationParams } from "./types"

export interface TransactionFilters extends PaginationParams {
  accountId?: string
  description?: string
  year?: number
  month?: number
}

export async function getTransactions(
  ledgerSlug: string,
  params?: TransactionFilters
): Promise<JsonApiListResponse<TransactionResource>> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions`, params as Record<string, string | number | undefined>)
}

// Lists every transaction in a category (the account plus all its
// subcategories) for the given month. Used by the report drill-down.
export async function getCategoryTransactions(
  ledgerSlug: string,
  accountId: string,
  year: number,
  month: number
): Promise<JsonApiListResponse<TransactionResource>> {
  return getTransactions(ledgerSlug, { accountId, year, month, "page[size]": 200 })
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
  envelopeId?: string
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

export interface MonthlyReport {
  id: string
  year: number
  month: number
  incomeByCurrency: Record<string, number>
  expensesByCurrency: Record<string, number>
}

export async function getMonthlyReport(
  ledgerSlug: string,
  year: number,
  month: number
): Promise<MonthlyReport> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions/monthly-report`, { year, month })
}

export interface MonthlyExpenseBreakdown {
  id: string
  year: number
  month: number
  expensesByAccountId: Record<string, number>
}

export async function getMonthlyExpenseBreakdown(
  ledgerSlug: string,
  year: number,
  month: number
): Promise<MonthlyExpenseBreakdown> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions/monthly-expense-breakdown`, { year, month })
}

export interface MonthlyIncomeBreakdown {
  id: string
  year: number
  month: number
  incomeByAccountId: Record<string, number>
}

export async function getMonthlyIncomeBreakdown(
  ledgerSlug: string,
  year: number,
  month: number
): Promise<MonthlyIncomeBreakdown> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions/monthly-income-breakdown`, { year, month })
}

export async function getTransactionDescriptions(ledgerSlug: string): Promise<string[]> {
  return apiClient.get(`/ledgers/${ledgerSlug}/transactions/descriptions`)
}

export async function getTransactionTemplates(ledgerSlug: string): Promise<TransactionResource[]> {
  const response: JsonApiListResponse<TransactionResource> = await apiClient.get(`/ledgers/${ledgerSlug}/transactions/templates`)
  return response.data
}
