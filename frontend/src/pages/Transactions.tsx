import { useEffect, useState, useMemo, useCallback } from "react"
import { Link, useLocation } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { TransactionDialog } from "@/components/transactions/TransactionDialog"
import { TransactionsTable } from "@/components/transactions/TransactionsTable"
import { TransactionCards } from "@/components/transactions/TransactionCards"
import { useAuth } from "@/contexts/AuthContext"
import { useLedger } from "@/contexts/LedgerContext"
import { getTransactions, deleteTransaction, createTransaction, updateTransaction, getTransactionTemplates } from "@/api/transactions"
import { getAccounts } from "@/api/accounts"
import { getAllInstruments } from "@/api/instruments"
import { getEnvelopes, getEnvelopeMappings } from "@/api/envelopes"
import type { TransactionResource, AccountResource, InstrumentResource, EnvelopeResource } from "@/api/types"
import {
  useTransactionEntries,
  entriesFromTransaction,
  emptyEntryPair,
  isTransactionFormValid,
  toEntryInputs,
  type TransactionEntryFormValue,
} from "@/hooks/useTransactionEntries"
import { useIsMobile } from "@/hooks/use-mobile"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useConfirm } from "@/components/ui/confirm-dialog"
import { Plus, ArrowLeftRight, X, Search } from "lucide-react"

interface PrefilledEntry {
  accountId: string
  currency: string
  type: "DEBIT" | "CREDIT"
}

interface LocationState {
  createTransaction?: boolean
  prefilledEntry?: PrefilledEntry
}

export function Transactions() {
  const { t } = useTranslation()
  const location = useLocation()
  const handleError = useErrorHandler()
  const confirm = useConfirm()
  const { isAuthenticated } = useAuth()
  const isMobile = useIsMobile()
  const locationState = location.state as LocationState | undefined
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [instruments, setInstruments] = useState<InstrumentResource[]>([])
  const [envelopes, setEnvelopes] = useState<EnvelopeResource[]>([])
  const [envelopeMappings, setEnvelopeMappings] = useState<Record<string, string>>({})
  const [transactionTemplates, setTransactionTemplates] = useState<TransactionResource[]>([])
  const { ledgers, currentLedger, setCurrentLedger } = useLedger()
  const selectedLedgerSlug = currentLedger?.attributes.slug ?? null
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [searchDescription, setSearchDescription] = useState("")
  const [activeSearch, setActiveSearch] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState<TransactionResource | null>(null)

  const selectedLedger = useMemo(
    () => ledgers.find((l) => l.attributes.slug === selectedLedgerSlug),
    [ledgers, selectedLedgerSlug]
  )
  const defaultCurrency = selectedLedger?.attributes.currency || "BRL"

  // Create form state
  const [date, setDate] = useState(new Date().toISOString().split("T")[0])
  const [description, setDescription] = useState("")
  const createForm = useTransactionEntries({ accounts, envelopeMappings, defaultCurrency })

  // Edit form state
  const [editDate, setEditDate] = useState("")
  const [editDescription, setEditDescription] = useState("")
  const editForm = useTransactionEntries({ accounts, envelopeMappings, defaultCurrency })

  const loadTransactions = useCallback(async (ledgerSlug: string, page: number, size: number, description?: string) => {
    const params: Record<string, string | number | undefined> = { "page[number]": page, "page[size]": size }
    if (description) {
      params.description = description
    }
    const response = await getTransactions(ledgerSlug, params)
    setTransactions(response.data)
    setTotalPages(response.meta?.page?.totalPages ?? 0)
    setTotalElements(response.meta?.page?.totalElements ?? 0)
    return response
  }, [])

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      loadTransactions(selectedLedgerSlug, currentPage, pageSize, activeSearch || undefined),
      getAccounts(selectedLedgerSlug, { "page[size]": 200 }),
      getAllInstruments(selectedLedgerSlug),
      getTransactionTemplates(selectedLedgerSlug),
      getEnvelopes(selectedLedgerSlug, { "page[size]": 200 }),
      getEnvelopeMappings(selectedLedgerSlug),
    ])
      .then(([, accountsResponse, instrumentsResponse, templates, envelopesResponse, mappings]) => {
        setAccounts(accountsResponse.data)
        setInstruments(instrumentsResponse)
        setTransactionTemplates(templates)
        setEnvelopes(envelopesResponse.data)
        setEnvelopeMappings(mappings)
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated, handleError, currentPage, pageSize, activeSearch, loadTransactions])

  // Handle navigation state to pre-fill transaction form
  const resetCreateEntries = createForm.reset
  useEffect(() => {
    if (locationState?.createTransaction && locationState.prefilledEntry && !isLoading && accounts.length > 0) {
      const prefilled = locationState.prefilledEntry

      resetCreateEntries([
        {
          accountId: prefilled.type === "CREDIT" ? prefilled.accountId : "",
          amount: "",
          currency: prefilled.type === "CREDIT" ? prefilled.currency : defaultCurrency,
          type: "CREDIT",
        },
        {
          accountId: prefilled.type === "DEBIT" ? prefilled.accountId : "",
          amount: "",
          currency: prefilled.type === "DEBIT" ? prefilled.currency : defaultCurrency,
          type: "DEBIT",
        },
      ])
      setIsCreateDialogOpen(true)

      // Clear the location state to prevent re-triggering
      window.history.replaceState({}, document.title)
    }
  }, [locationState, isLoading, accounts.length, defaultCurrency, resetCreateEntries])

  const handleDelete = async (transactionId: string) => {
    if (!selectedLedgerSlug) return
    if (!(await confirm({ description: t("transactions.confirmDelete") }))) return

    try {
      await deleteTransaction(selectedLedgerSlug, transactionId)
      await loadTransactions(selectedLedgerSlug, currentPage, pageSize)
    } catch (error) {
      handleError(error, "deleteFailed")
    }
  }

  const resetCreateForm = () => {
    setDate(new Date().toISOString().split("T")[0])
    setDescription("")
    createForm.reset()
  }

  // Populate entries from a template when a description is selected
  const templateEntries = (template: TransactionResource): TransactionEntryFormValue[] =>
    entriesFromTransaction(template, defaultCurrency)

  const handleSubmit = async () => {
    if (!selectedLedgerSlug || !isTransactionFormValid(createForm.entries, date, description)) return

    setIsSubmitting(true)
    try {
      const response = await createTransaction(selectedLedgerSlug, {
        date,
        description,
        entries: toEntryInputs(createForm.entries),
      })

      // Update template cache with the new transaction (replaces existing template for this description)
      setTransactionTemplates((prev) => {
        const filtered = prev.filter((t) => t.attributes.description !== description)
        return [...filtered, response.data].sort((a, b) =>
          a.attributes.description.localeCompare(b.attributes.description)
        )
      })
      setCurrentPage(0)
      await loadTransactions(selectedLedgerSlug, 0, pageSize)
      setIsCreateDialogOpen(false)
      resetCreateForm()
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEdit = (transaction: TransactionResource) => {
    setEditingTransaction(transaction)
    setEditDate(transaction.attributes.date)
    setEditDescription(transaction.attributes.description)

    const entries = entriesFromTransaction(transaction, defaultCurrency)
    editForm.reset(entries.length > 0 ? entries : emptyEntryPair(defaultCurrency))
    setIsEditDialogOpen(true)
  }

  const handleUpdate = async () => {
    if (!selectedLedgerSlug || !editingTransaction || !isTransactionFormValid(editForm.entries, editDate, editDescription)) return

    setIsSubmitting(true)
    try {
      await updateTransaction(selectedLedgerSlug, editingTransaction.id, {
        date: editDate,
        description: editDescription,
        entries: toEntryInputs(editForm.entries),
      })

      await loadTransactions(selectedLedgerSlug, currentPage, pageSize)
      setIsEditDialogOpen(false)
      setEditingTransaction(null)
    } catch (error) {
      handleError(error, "updateFailed")
    } finally {
      setIsSubmitting(false)
    }
  }

  const dialogSharedProps = {
    accounts,
    instruments,
    envelopes,
    templates: transactionTemplates,
    currency: defaultCurrency,
    isSubmitting,
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
        <Button
          size={isMobile ? "sm" : "default"}
          disabled={!selectedLedgerSlug || accounts.length === 0}
          onClick={() => setIsCreateDialogOpen(true)}
        >
          <Plus className="mr-1 md:mr-2 h-4 w-4" />
          {isMobile ? t("common.create") : t("transactions.newTransaction")}
        </Button>
      </div>

      <TransactionDialog
        mode="create"
        open={isCreateDialogOpen}
        onOpenChange={(open) => {
          setIsCreateDialogOpen(open)
          if (!open) resetCreateForm()
        }}
        date={date}
        onDateChange={setDate}
        description={description}
        onDescriptionChange={setDescription}
        entries={createForm.entries}
        onAddEntry={createForm.addEntry}
        onRemoveEntry={createForm.removeEntry}
        onUpdateEntry={createForm.updateEntry}
        onAmountBlur={createForm.handleAmountBlur}
        onTemplateSelect={(template) => {
          const entries = templateEntries(template)
          if (entries.length > 0) createForm.reset(entries)
        }}
        onSubmit={handleSubmit}
        {...dialogSharedProps}
      />

      <TransactionDialog
        mode="edit"
        open={isEditDialogOpen}
        onOpenChange={(open) => {
          setIsEditDialogOpen(open)
          if (!open) setEditingTransaction(null)
        }}
        date={editDate}
        onDateChange={setEditDate}
        description={editDescription}
        onDescriptionChange={setEditDescription}
        entries={editForm.entries}
        onAddEntry={editForm.addEntry}
        onRemoveEntry={editForm.removeEntry}
        onUpdateEntry={editForm.updateEntry}
        onAmountBlur={editForm.handleAmountBlur}
        onTemplateSelect={(template) => {
          const entries = templateEntries(template)
          if (entries.length > 0) editForm.reset(entries)
        }}
        onSubmit={handleUpdate}
        {...dialogSharedProps}
      />

      {ledgers.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {ledgers.map((ledger) => (
            <Button
              key={ledger.id}
              variant={selectedLedgerSlug === ledger.attributes.slug ? "default" : "outline"}
              size="sm"
              onClick={() => { setCurrentLedger(ledger.attributes.slug); setCurrentPage(0) }}
            >
              {ledger.attributes.name}
            </Button>
          ))}
        </div>
      )}

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
                <Button type="submit" variant="secondary" size="icon">
                  <Search className="h-4 w-4" />
                </Button>
                {activeSearch && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
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
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : !selectedLedgerSlug ? (
            <div className="flex flex-col items-center justify-center py-12">
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("transactions.noLedgerSelected")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("transactions.selectLedgerDescription")}
              </p>
              <Link to="/ledgers">
                <Button variant="outline">{t("transactions.goToLedgers")}</Button>
              </Link>
            </div>
          ) : transactions.length > 0 ? (
            <>
            {isMobile ? (
              <TransactionCards transactions={transactions} onEdit={handleEdit} onDelete={handleDelete} />
            ) : (
              <TransactionsTable transactions={transactions} onEdit={handleEdit} onDelete={handleDelete} />
            )}
            <Pagination page={currentPage} totalPages={totalPages} onPageChange={setCurrentPage}>
              <span>{t("transactions.totalTransactions", { count: totalElements })}</span>
              <span className="text-muted-foreground/50">·</span>
              <Select value={String(pageSize)} onValueChange={(value) => { setPageSize(Number(value)); setCurrentPage(0) }}>
                <SelectTrigger className="h-8 w-[70px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="10">10</SelectItem>
                  <SelectItem value="20">20</SelectItem>
                  <SelectItem value="50">50</SelectItem>
                </SelectContent>
              </Select>
              <span>{t("transactions.perPage")}</span>
            </Pagination>
            </>
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("transactions.noTransactions")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("transactions.noTransactionsDescription")}
              </p>
              <Button onClick={() => setIsCreateDialogOpen(true)} disabled={accounts.length === 0}>
                <Plus className="mr-2 h-4 w-4" />
                {t("transactions.createTransaction")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
