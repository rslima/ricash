import { useEffect, useState } from "react"
import { Link, useLocation } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { SignInRequired } from "@/components/SignInRequired"
import { EmptyState } from "@/components/EmptyState"
import { TableSkeleton } from "@/components/ui/table-skeleton"
import { LedgerSelector } from "@/components/LedgerSelector"
import { TablePagination } from "@/components/TablePagination"
import { TransactionFormDialog } from "@/components/transactions/TransactionFormDialog"
import { ExportTransactionsDialog } from "@/components/ExportTransactionsDialog"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useAuth } from "@/contexts/AuthContext"
import type { TransactionEntryInput, TransactionFilters } from "@/api/transactions"
import { useAccounts } from "@/api/accounts.hooks"
import { useAllInstruments } from "@/api/instruments.hooks"
import { useEnvelopes, useEnvelopeMappings } from "@/api/envelopes.hooks"
import {
  useTransactions,
  useTransactionTemplates,
  useCreateTransaction,
  useUpdateTransaction,
  useDeleteTransaction,
} from "@/api/transactions.hooks"
import type { TransactionResource } from "@/api/types"
import type { TransactionEntry } from "@/lib/transaction-entries"
import { formatCurrency, formatDate } from "@/lib/utils"
import { useIsMobile } from "@/hooks/use-mobile"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useLedgerSelection } from "@/hooks/use-ledger-selection"
import { usePagination } from "@/hooks/use-pagination"
import { useTransactionForm } from "@/hooks/use-transaction-form"
import { combineQueries, useQueryErrorToast } from "@/hooks/use-query-error-toast"
import { Plus, Trash2, ArrowLeftRight, MoreHorizontal, X, Pencil, Search, Download } from "lucide-react"

interface PrefilledEntry {
  accountId: string
  currency: string
  type: "DEBIT" | "CREDIT"
}

interface LocationState {
  createTransaction?: boolean
  prefilledEntry?: PrefilledEntry
}

function toEntryInputs(entries: readonly TransactionEntry[]): TransactionEntryInput[] {
  return entries.map((e) => ({
    accountId: e.accountId,
    amount: parseFloat(e.amount),
    currency: e.currency,
    toAmount: e.toAmount ? parseFloat(e.toAmount) : undefined,
    toCurrency: e.toCurrency || undefined,
    type: e.type,
    instrumentId: e.instrumentId || undefined,
    quantity: e.quantity ? parseFloat(e.quantity) : undefined,
    envelopeId: e.envelopeId || undefined,
  }))
}

export function Transactions() {
  const { t } = useTranslation()
  const location = useLocation()
  const handleError = useErrorHandler()
  const { isAuthenticated } = useAuth()
  const isMobile = useIsMobile()
  const locationState = location.state as LocationState | undefined
  const {
    ledgers,
    selectedLedgerSlug,
    setSelectedLedgerSlug,
    selectedLedger,
    ledgerCurrency,
    isLoading: ledgersLoading,
    isError: ledgersError,
    error: ledgersErrorValue,
  } = useLedgerSelection()
  const { currentPage, setCurrentPage, pageSize, setPageSize } = usePagination(20)
  const [searchDescription, setSearchDescription] = useState("")
  const [activeSearch, setActiveSearch] = useState("")
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isExportDialogOpen, setIsExportDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState<TransactionResource | null>(null)

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  // Queries are gated on auth + a selected ledger; the page/search filters feed
  // the transactions query key so changing them drives a refetch.
  const dataEnabled = isAuthenticated && !!selectedLedgerSlug
  const filters: TransactionFilters = {
    "page[number]": currentPage,
    "page[size]": pageSize,
    ...(activeSearch ? { description: activeSearch } : {}),
  }

  const txQuery = useTransactions(selectedLedgerSlug ?? "", filters, dataEnabled)
  const accountsQuery = useAccounts(selectedLedgerSlug ?? "", { "page[size]": 200 }, dataEnabled)
  const instrumentsQuery = useAllInstruments(selectedLedgerSlug ?? "", dataEnabled)
  const templatesQuery = useTransactionTemplates(selectedLedgerSlug ?? "", dataEnabled)
  const envelopesQuery = useEnvelopes(selectedLedgerSlug ?? "", { "page[size]": 200 }, dataEnabled)
  const mappingsQuery = useEnvelopeMappings(selectedLedgerSlug ?? "", dataEnabled)

  const createTransactionMutation = useCreateTransaction(selectedLedgerSlug ?? "")
  const updateTransactionMutation = useUpdateTransaction(selectedLedgerSlug ?? "")
  const deleteTransactionMutation = useDeleteTransaction(selectedLedgerSlug ?? "")

  // Read query results (hooks pass the API response straight through).
  const transactions = txQuery.data?.data ?? []
  const accounts = accountsQuery.data?.data ?? []
  const instruments = instrumentsQuery.data ?? []
  const envelopes = envelopesQuery.data?.data ?? []
  const envelopeMappings = mappingsQuery.data ?? {}
  const transactionTemplates = templatesQuery.data ?? []
  const totalPages = txQuery.data?.meta?.page?.totalPages ?? 0
  const totalElements = txQuery.data?.meta?.page?.totalElements ?? 0

  // One form instance per dialog; each owns its date/description/entries state.
  const createForm = useTransactionForm({ defaultCurrency: ledgerCurrency, accounts, envelopeMappings })
  const editForm = useTransactionForm({ defaultCurrency: ledgerCurrency, accounts, envelopeMappings })

  // Reproduce the old single loading flag: skeleton until the ledger's data is present.
  // Surface any fetch failure as a toast, preserving the old "fetchFailed" key.
  const queries = [
    { isLoading: ledgersLoading, isError: ledgersError, error: ledgersErrorValue },
    txQuery,
    accountsQuery,
    instrumentsQuery,
    templatesQuery,
    envelopesQuery,
    mappingsQuery,
  ]
  const { isLoading } = combineQueries(...queries)
  useQueryErrorToast(queries)

  const { setEntries: setCreateEntries } = createForm

  // Handle navigation state to pre-fill transaction form
  useEffect(() => {
    if (locationState?.createTransaction && locationState.prefilledEntry && !isLoading && accounts.length > 0) {
      const prefilled = locationState.prefilledEntry

      // Create entries with the pre-filled account
      const newEntries: TransactionEntry[] = [
        {
          accountId: prefilled.type === "CREDIT" ? prefilled.accountId : "",
          amount: "",
          currency: prefilled.type === "CREDIT" ? prefilled.currency : ledgerCurrency,
          type: "CREDIT",
        },
        {
          accountId: prefilled.type === "DEBIT" ? prefilled.accountId : "",
          amount: "",
          currency: prefilled.type === "DEBIT" ? prefilled.currency : ledgerCurrency,
          type: "DEBIT",
        },
      ]

      setCreateEntries(newEntries)
      setIsCreateDialogOpen(true)

      // Clear the location state to prevent re-triggering
      window.history.replaceState({}, document.title)
    }
  }, [locationState, isLoading, accounts.length, ledgerCurrency, setCreateEntries])

  const handleDelete = (transactionId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("transactions.confirmDelete"))) return

    deleteTransactionMutation.mutate(transactionId, {
      onError: (error) => handleError(error, "deleteFailed"),
    })
  }

  const handleSubmit = () => {
    if (!selectedLedgerSlug || !createForm.isValid) return

    createTransactionMutation.mutate(
      {
        date: createForm.date,
        description: createForm.description,
        entries: toEntryInputs(createForm.entries),
      },
      {
        onSuccess: () => {
          // Jump to the first page so the new transaction is visible; the mutation
          // already invalidated transactions (incl. templates), accounts, envelopes.
          setCurrentPage(0)
          setIsCreateDialogOpen(false)
          createForm.reset()
        },
        onError: (error) => handleError(error, "createFailed"),
      }
    )
  }

  const handleEdit = (transaction: TransactionResource) => {
    setEditingTransaction(transaction)
    editForm.loadFromTransaction(transaction)
    setIsEditDialogOpen(true)
  }

  const handleUpdate = () => {
    if (!selectedLedgerSlug || !editingTransaction || !editForm.isValid) return

    updateTransactionMutation.mutate(
      {
        transactionId: editingTransaction.id,
        data: {
          date: editForm.date,
          description: editForm.description,
          entries: toEntryInputs(editForm.entries),
        },
      },
      {
        onSuccess: () => {
          setIsEditDialogOpen(false)
          setEditingTransaction(null)
        },
        onError: (error) => handleError(error, "updateFailed"),
      }
    )
  }

  if (!isAuthenticated) {
    return <SignInRequired resourceKey="nav.transactions" />
  }

  return (
    <div className="space-y-4 md:space-y-6">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="text-xl md:text-2xl font-bold tracking-tight">{t("transactions.title")}</h2>
          <p className="text-sm text-muted-foreground">
            {t("transactions.subtitle")}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size={isMobile ? "sm" : "default"}
            disabled={!selectedLedgerSlug}
            onClick={() => setIsExportDialogOpen(true)}
          >
            <Download className="mr-1 md:mr-2 h-4 w-4" />
            {!isMobile && t("transactions.export.export")}
          </Button>
          <Button
            size={isMobile ? "sm" : "default"}
            disabled={!selectedLedgerSlug || accounts.length === 0}
            onClick={() => setIsCreateDialogOpen(true)}
          >
            <Plus className="mr-1 md:mr-2 h-4 w-4" />
            {isMobile ? t("common.create") : t("transactions.newTransaction")}
          </Button>
        </div>
      </div>

      {selectedLedgerSlug && (
        <ExportTransactionsDialog
          ledgerSlug={selectedLedgerSlug}
          accounts={accounts}
          open={isExportDialogOpen}
          onOpenChange={setIsExportDialogOpen}
        />
      )}

      <TransactionFormDialog
        mode="create"
        open={isCreateDialogOpen}
        onOpenChange={(open) => {
          setIsCreateDialogOpen(open)
          if (!open) createForm.reset()
        }}
        form={createForm}
        accounts={accounts}
        instruments={instruments}
        envelopes={envelopes}
        templates={transactionTemplates}
        onSubmit={handleSubmit}
        isPending={createTransactionMutation.isPending}
      />

      <TransactionFormDialog
        mode="edit"
        open={isEditDialogOpen}
        onOpenChange={(open) => {
          setIsEditDialogOpen(open)
          if (!open) setEditingTransaction(null)
        }}
        form={editForm}
        accounts={accounts}
        instruments={instruments}
        envelopes={envelopes}
        templates={transactionTemplates}
        onSubmit={handleUpdate}
        isPending={updateTransactionMutation.isPending}
      />

      <LedgerSelector
        ledgers={ledgers}
        selectedSlug={selectedLedgerSlug}
        onSelect={(slug) => {
          setSelectedLedgerSlug(slug)
          setCurrentPage(0)
        }}
      />

      <Card>
        <CardHeader>
          <CardTitle>
            {selectedLedger
              ? `${t("transactions.title")} - ${selectedLedger.attributes.name}`
              : t("transactions.noLedgerSelected")}
          </CardTitle>
          <CardDescription>
            {t("transactions.subtitle")}
          </CardDescription>
          {selectedLedgerSlug && (
            <form
              className="mt-4"
              onSubmit={(e) => {
                e.preventDefault()
                setActiveSearch(searchDescription)
                setCurrentPage(0)
              }}
            >
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder={t("transactions.searchByDescription")}
                    value={searchDescription}
                    onChange={(e) => setSearchDescription(e.target.value)}
                    className="pl-9"
                  />
                </div>
                <Button type="submit" variant="secondary" size="icon" aria-label={t("common.search")}>
                  <Search className="h-4 w-4" />
                </Button>
                {activeSearch && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label={t("common.clear")}
                    onClick={() => {
                      setSearchDescription("")
                      setActiveSearch("")
                      setCurrentPage(0)
                    }}
                  >
                    <X className="h-4 w-4" />
                  </Button>
                )}
              </div>
            </form>
          )}
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <TableSkeleton />
          ) : !selectedLedgerSlug ? (
            <EmptyState
              icon={ArrowLeftRight}
              title={t("transactions.noLedgerSelected")}
              description={t("transactions.selectLedgerDescription")}
              action={
                <Link to="/ledgers">
                  <Button variant="outline">{t("transactions.goToLedgers")}</Button>
                </Link>
              }
            />
          ) : transactions.length > 0 ? (
            <>
            {isMobile ? (
            <div className="space-y-2">
              {transactions.map((transaction) => (
                <div
                  key={transaction.id}
                  className="rounded-lg border bg-card p-3"
                >
                  <div className="flex items-start justify-between">
                    <div className="min-w-0 flex-1">
                      <p className="font-medium truncate">{transaction.attributes.description}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">
                        {formatDate(transaction.attributes.date)}
                      </p>
                    </div>
                    <div className="flex items-center gap-1 ml-2 shrink-0">
                      <span className="font-mono text-sm font-medium">
                        {formatCurrency(
                          transaction.attributes.amount,
                          transaction.attributes.currency
                        )}
                      </span>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label={t("common.actions")}>
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleEdit(transaction)}>
                            <Pencil className="mr-2 h-4 w-4" />
                            {t("common.edit")}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => handleDelete(transaction.id)}
                            className="text-destructive"
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            {t("common.delete")}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-1 mt-2">
                    {transaction.attributes.entries?.map((entry, idx) => (
                      <Badge
                        key={idx}
                        variant={entry.type === "DEBIT" ? "secondary" : "outline"}
                        className="text-[10px]"
                      >
                        {entry.accountName}: {entry.type === "DEBIT" ? "DB" : "CR"}
                        {entry.instrumentSymbol && ` (${entry.instrumentSymbol})`}
                        {entry.quantity && ` x${entry.quantity}`}
                      </Badge>
                    ))}
                  </div>
                </div>
              ))}
            </div>
            ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.date")}</TableHead>
                  <TableHead>{t("common.description")}</TableHead>
                  <TableHead>{t("transactions.entries")}</TableHead>
                  <TableHead className="text-right">{t("common.amount")}</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map((transaction) => (
                  <TableRow key={transaction.id}>
                    <TableCell className="font-medium">
                      {formatDate(transaction.attributes.date)}
                    </TableCell>
                    <TableCell>{transaction.attributes.description}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {transaction.attributes.entries?.map((entry, idx) => (
                          <Badge
                            key={idx}
                            variant={entry.type === "DEBIT" ? "secondary" : "outline"}
                          >
                            {entry.accountName}: {entry.type === "DEBIT" ? "DB" : "CR"}
                            {entry.instrumentSymbol && ` (${entry.instrumentSymbol})`}
                            {entry.quantity && ` x${entry.quantity}`}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-mono">
                      {formatCurrency(
                        transaction.attributes.amount,
                        transaction.attributes.currency
                      )}
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" aria-label={t("common.actions")}>
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleEdit(transaction)}>
                            <Pencil className="mr-2 h-4 w-4" />
                            {t("common.edit")}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => handleDelete(transaction.id)}
                            className="text-destructive"
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            {t("common.delete")}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            )}
            <TablePagination
              currentPage={currentPage}
              totalPages={totalPages}
              totalElements={totalElements}
              pageSize={pageSize}
              onPageChange={setCurrentPage}
              onPageSizeChange={setPageSize}
            />
            </>
          ) : (
            <EmptyState
              icon={ArrowLeftRight}
              title={t("transactions.noTransactions")}
              description={t("transactions.noTransactionsDescription")}
              action={
                <Button onClick={() => setIsCreateDialogOpen(true)} disabled={accounts.length === 0}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t("transactions.createTransaction")}
                </Button>
              }
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
