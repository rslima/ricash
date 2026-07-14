import { apiClient } from "./client"

export type ExportFormat = "csv" | "ofx"

export interface ExportTransactionsOptions {
  format: ExportFormat
  accountId?: string
  includeChildren?: boolean
  from?: string // yyyy-MM-dd
  to?: string // yyyy-MM-dd
}

export async function downloadTransactionsExport(
  ledgerSlug: string,
  options: ExportTransactionsOptions
): Promise<void> {
  const { blob, filename } = await apiClient.getBlob(
    `/ledgers/${ledgerSlug}/transactions/export`,
    {
      format: options.format,
      accountId: options.accountId || undefined,
      includeChildren: options.accountId && options.includeChildren ? true : undefined,
      from: options.from || undefined,
      to: options.to || undefined,
    }
  )

  triggerBrowserDownload(blob, filename ?? `${ledgerSlug}-transactions.${options.format}`)
}

export function triggerBrowserDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
