import { useEffect, useState, useMemo, useCallback } from "react"
import { useParams, Link, useLocation } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { AccountAutocomplete } from "@/components/AccountAutocomplete"
import { DescriptionAutocomplete } from "@/components/DescriptionAutocomplete"
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useAuth } from "@/contexts/AuthContext"
import { getTransactions, deleteTransaction, createTransaction, updateTransaction, getTransactionTemplates, type TransactionEntryInput } from "@/api/transactions"
import { getLedgers } from "@/api/ledgers"
import { getAccounts } from "@/api/accounts"
import { getAllInstruments } from "@/api/instruments"
import { getEnvelopes, getEnvelopeMappings } from "@/api/envelopes"
import type { TransactionResource, LedgerResource, AccountResource, InstrumentResource, EnvelopeResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { useIsMobile } from "@/hooks/use-mobile"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { Plus, Trash2, ArrowLeftRight, MoreHorizontal, X, Pencil, ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, Search } from "lucide-react"

interface TransactionEntry {
  accountId: string
  amount: string
  currency: string
  toAmount?: string
  toCurrency?: string
  type: "DEBIT" | "CREDIT"
  instrumentId?: string
  quantity?: string
  envelopeId?: string
}

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
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const location = useLocation()
  const handleError = useErrorHandler()
  const { isAuthenticated } = useAuth()
  const isMobile = useIsMobile()
  const locationState = location.state as LocationState | undefined
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [instruments, setInstruments] = useState<InstrumentResource[]>([])
  const [envelopes, setEnvelopes] = useState<EnvelopeResource[]>([])
  const [envelopeMappings, setEnvelopeMappings] = useState<Record<string, string>>({})
  const [transactionTemplates, setTransactionTemplates] = useState<TransactionResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
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

  // Form state
  const [date, setDate] = useState(new Date().toISOString().split("T")[0])
  const [description, setDescription] = useState("")
  const [entries, setEntries] = useState<TransactionEntry[]>([
    { accountId: "", amount: "", currency: "BRL", type: "CREDIT" },
    { accountId: "", amount: "", currency: "BRL", type: "DEBIT" },
  ])

  // Edit form state
  const [editDate, setEditDate] = useState("")
  const [editDescription, setEditDescription] = useState("")
  const [editEntries, setEditEntries] = useState<TransactionEntry[]>([])

  // Compute selected ledger
  const selectedLedger = useMemo(
    () => ledgers.find((l) => l.attributes.slug === selectedLedgerSlug),
    [ledgers, selectedLedgerSlug]
  )

  useEffect(() => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    getLedgers()
      .then((response) => {
        setLedgers(response.data)
        if (response.data.length > 0) {
          setSelectedLedgerSlug(prev => prev ?? response.data[0].attributes.slug)
        }
      })
      .catch((e) => handleError(e, "fetchFailed"))
  }, [isAuthenticated, handleError])

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
  useEffect(() => {
    if (locationState?.createTransaction && locationState.prefilledEntry && !isLoading && accounts.length > 0) {
      const prefilled = locationState.prefilledEntry
      const defaultCurrency = selectedLedger?.attributes.currency || "BRL"

      // Create entries with the pre-filled account
      const newEntries: TransactionEntry[] = [
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
      ]

      setEntries(newEntries)
      setIsCreateDialogOpen(true)

      // Clear the location state to prevent re-triggering
      window.history.replaceState({}, document.title)
    }
  }, [locationState, isLoading, accounts.length, selectedLedger])

  const handleDelete = async (transactionId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("transactions.confirmDelete"))) return

    try {
      await deleteTransaction(selectedLedgerSlug, transactionId)
      await loadTransactions(selectedLedgerSlug, currentPage, pageSize)
    } catch (error) {
      handleError(error, "deleteFailed")
    }
  }

  const resetForm = () => {
    setDate(new Date().toISOString().split("T")[0])
    setDescription("")
    setEntries([
      { accountId: "", amount: "", currency: selectedLedger?.attributes.currency || "BRL", type: "CREDIT" },
      { accountId: "", amount: "", currency: selectedLedger?.attributes.currency || "BRL", type: "DEBIT" },
    ])
  }

  const addEntry = (type: "DEBIT" | "CREDIT", isEdit = false) => {
    const defaultCurrency = selectedLedger?.attributes.currency || "BRL"
    if (isEdit) {
      setEditEntries([...editEntries, { accountId: "", amount: "", currency: defaultCurrency, type }])
    } else {
      setEntries([...entries, { accountId: "", amount: "", currency: defaultCurrency, type }])
    }
  }

  const removeEntry = (index: number, isEdit = false) => {
    if (isEdit) {
      if (editEntries.length > 2) {
        setEditEntries(editEntries.filter((_, i) => i !== index))
      }
    } else {
      if (entries.length > 2) {
        setEntries(entries.filter((_, i) => i !== index))
      }
    }
  }

  const updateEntry = (index: number, field: keyof TransactionEntry, value: string, isEdit = false) => {
    if (isEdit) {
      const newEntries = [...editEntries]
      newEntries[index] = { ...newEntries[index], [field]: value }

      // Auto-fill toCurrency when account is selected and needs conversion
      if (field === "accountId" && value) {
        const account = accounts.find(a => a.id === value)
        const entryCurrency = newEntries[index].currency
        if (account && account.attributes.currency !== entryCurrency) {
          newEntries[index].toCurrency = account.attributes.currency
        } else {
          // Clear conversion fields if same currency
          newEntries[index].toAmount = undefined
          newEntries[index].toCurrency = undefined
        }
        // Auto-fill envelope from account mapping
        if (!newEntries[index].envelopeId && envelopeMappings[value]) {
          newEntries[index].envelopeId = envelopeMappings[value]
        }
      }

      // Auto-update toCurrency when currency changes
      if (field === "currency" && newEntries[index].accountId) {
        const account = accounts.find(a => a.id === newEntries[index].accountId)
        if (account && account.attributes.currency !== value) {
          newEntries[index].toCurrency = account.attributes.currency
        } else {
          // Clear conversion fields if same currency
          newEntries[index].toAmount = undefined
          newEntries[index].toCurrency = undefined
        }
      }

      setEditEntries(newEntries)
    } else {
      const newEntries = [...entries]
      newEntries[index] = { ...newEntries[index], [field]: value }

      // Auto-fill toCurrency when account is selected and needs conversion
      if (field === "accountId" && value) {
        const account = accounts.find(a => a.id === value)
        const entryCurrency = newEntries[index].currency
        if (account && account.attributes.currency !== entryCurrency) {
          newEntries[index].toCurrency = account.attributes.currency
        } else {
          // Clear conversion fields if same currency
          newEntries[index].toAmount = undefined
          newEntries[index].toCurrency = undefined
        }
        // Auto-fill envelope from account mapping
        if (!newEntries[index].envelopeId && envelopeMappings[value]) {
          newEntries[index].envelopeId = envelopeMappings[value]
        }
      }

      // Auto-update toCurrency when currency changes
      if (field === "currency" && newEntries[index].accountId) {
        const account = accounts.find(a => a.id === newEntries[index].accountId)
        if (account && account.attributes.currency !== value) {
          newEntries[index].toCurrency = account.attributes.currency
        } else {
          // Clear conversion fields if same currency
          newEntries[index].toAmount = undefined
          newEntries[index].toCurrency = undefined
        }
      }

      setEntries(newEntries)
    }
  }

  const calculateTotal = (type: "DEBIT" | "CREDIT", entryList: TransactionEntry[]) => {
    return entryList
      .filter((e) => e.type === type)
      .reduce((sum, e) => sum + (parseFloat(e.amount) || 0), 0)
  }

  // Safe number parser to avoid NaN propagation
  const parseAmount = (value: string | undefined): number => {
    if (!value || value.trim() === "") return 0
    const parsed = parseFloat(value)
    return isNaN(parsed) ? 0 : parsed
  }

  // Check if an amount is considered "empty" for auto-balance purposes
  const isAmountEmpty = (amount: string | undefined): boolean => {
    if (!amount || amount.trim() === "") return true
    const parsed = parseFloat(amount)
    return isNaN(parsed) || parsed === 0
  }

  // Auto-balance: if exactly one entry has an empty/zero amount, fill it with the value needed to balance
  const autoBalanceEntries = (entryList: TransactionEntry[]): TransactionEntry[] => {
    // Group entries by currency
    const byCurrency: Record<string, { entries: { index: number; entry: TransactionEntry }[]; debits: number; credits: number }> = {}

    entryList.forEach((entry, index) => {
      const currency = entry.currency
      if (!currency) return

      if (!byCurrency[currency]) {
        byCurrency[currency] = { entries: [], debits: 0, credits: 0 }
      }

      byCurrency[currency].entries.push({ index, entry })

      // Only count non-empty amounts for balance calculation
      if (!isAmountEmpty(entry.amount)) {
        const amount = parseAmount(entry.amount)
        if (entry.type === "DEBIT") {
          byCurrency[currency].debits += amount
        } else {
          byCurrency[currency].credits += amount
        }
      }
    })

    // Check each currency group
    const newEntries = [...entryList]
    let modified = false

    for (const currency of Object.keys(byCurrency)) {
      const group = byCurrency[currency]

      // Find entries with empty/zero amount in this currency
      const emptyEntries = group.entries.filter(({ entry }) => isAmountEmpty(entry.amount))

      // Only auto-fill if exactly one entry is empty/zero
      if (emptyEntries.length !== 1) continue

      const { index: emptyIndex, entry: emptyEntry } = emptyEntries[0]

      // Calculate the balance needed
      const { debits, credits } = group

      let neededAmount: number
      if (emptyEntry.type === "DEBIT") {
        // Need more debits to balance credits
        neededAmount = credits - debits
      } else {
        // Need more credits to balance debits
        neededAmount = debits - credits
      }

      // Only fill if the needed amount is positive
      if (neededAmount > 0) {
        newEntries[emptyIndex] = {
          ...newEntries[emptyIndex],
          amount: neededAmount.toFixed(2)
        }
        modified = true
      }
    }

    return modified ? newEntries : entryList
  }

  // Trigger auto-balance when user finishes editing an amount field
  const handleAmountBlur = (isEdit: boolean) => {
    if (isEdit) {
      const balanced = autoBalanceEntries(editEntries)
      if (balanced !== editEntries) {
        setEditEntries(balanced)
      }
    } else {
      const balanced = autoBalanceEntries(entries)
      if (balanced !== entries) {
        setEntries(balanced)
      }
    }
  }

  // Populate form fields from a template when a description is selected
  const handleTemplateSelect = (template: TransactionResource, isEdit: boolean) => {
    const templateEntries: TransactionEntry[] = template.attributes.entries?.map((entry) => ({
      accountId: entry.accountId || "",
      amount: entry.amount?.toString() || "",
      currency: entry.currency || selectedLedger?.attributes.currency || "BRL",
      toAmount: entry.toAmount?.toString(),
      toCurrency: entry.toCurrency,
      type: entry.type || "DEBIT",
      instrumentId: entry.instrumentId,
      quantity: entry.quantity?.toString(),
      envelopeId: entry.envelopeId,
    })) || []

    if (isEdit) {
      setEditEntries(templateEntries.length > 0 ? templateEntries : editEntries)
    } else {
      setEntries(templateEntries.length > 0 ? templateEntries : entries)
    }
  }

  // Multi-currency balance validation:
  // Must match backend logic in TransactionServiceBean.validateMultiCurrencyBalance()
  // Group by ORIGINAL currency (entry.currency) and use ORIGINAL amount (entry.amount)
  // This allows multi-currency transactions to balance in the transaction currency
  const isBalanced = (entryList: TransactionEntry[]) => {
    // Early return if no entries
    if (!entryList || entryList.length === 0) {
      return false
    }

    // Group by ORIGINAL currency (the transaction currency, not the account currency)
    const byCurrency: Record<string, { debits: number, credits: number }> = {}

    for (const entry of entryList) {
      const currency = entry.currency
      if (!currency) continue

      if (!byCurrency[currency]) {
        byCurrency[currency] = { debits: 0, credits: 0 }
      }

      // Use the ORIGINAL amount (transaction currency)
      const amount = parseAmount(entry.amount)

      if (entry.type === "DEBIT") {
        byCurrency[currency].debits += amount
      } else {
        byCurrency[currency].credits += amount
      }
    }

    // Check if all currencies are balanced
    const currencies = Object.keys(byCurrency)
    if (currencies.length === 0) {
      return false
    }

    // Each currency must have debits ≈ credits
    return currencies.every(currency => {
      const { debits, credits } = byCurrency[currency]
      return Math.abs(debits - credits) < 0.01
    })
  }

  const isFormValid = (entryList: TransactionEntry[], dateValue: string, descValue: string) => {
    // Check basic fields
    if (!dateValue || !descValue.trim()) {
      return false
    }

    // Check each entry
    const entriesValid = entryList.every((e) => {
      // Must have account and amount
      if (!e.accountId || !e.amount || parseFloat(e.amount) <= 0) {
        return false
      }

      // toAmount is optional - if provided, it must be positive
      if (e.toAmount && parseFloat(e.toAmount) < 0) {
        return false
      }

      return true
    })

    if (!entriesValid) {
      return false
    }

    return isBalanced(entryList)
  }

  const handleSubmit = async () => {
    if (!selectedLedgerSlug || !isFormValid(entries, date, description)) return

    setIsSubmitting(true)
    try {
      const transactionEntries: TransactionEntryInput[] = entries.map((e) => ({
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

      const response = await createTransaction(selectedLedgerSlug, {
        date,
        description,
        entries: transactionEntries,
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
      resetForm()
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

    // Convert entries from the transaction
    const defaultCurrency = selectedLedger?.attributes.currency || "BRL"
    const transactionEntries: TransactionEntry[] = transaction.attributes.entries?.map((entry) => ({
      accountId: entry.accountId || "",
      amount: entry.amount?.toString() || "0",
      currency: entry.currency || defaultCurrency,
      toAmount: entry.toAmount?.toString(),
      toCurrency: entry.toCurrency,
      type: entry.type || "DEBIT",
      instrumentId: entry.instrumentId,
      quantity: entry.quantity?.toString(),
      envelopeId: entry.envelopeId,
    })) || []

    setEditEntries(transactionEntries.length > 0 ? transactionEntries : [
      { accountId: "", amount: "", currency: selectedLedger?.attributes.currency || "BRL", type: "CREDIT" },
      { accountId: "", amount: "", currency: selectedLedger?.attributes.currency || "BRL", type: "DEBIT" },
    ])
    setIsEditDialogOpen(true)
  }

  const handleUpdate = async () => {
    if (!selectedLedgerSlug || !editingTransaction || !isFormValid(editEntries, editDate, editDescription)) return

    setIsSubmitting(true)
    try {
      const transactionEntries: TransactionEntryInput[] = editEntries.map((e) => ({
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

      await updateTransaction(selectedLedgerSlug, editingTransaction.id, {
        date: editDate,
        description: editDescription,
        entries: transactionEntries,
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

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.transactions").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  const debitTotal = calculateTotal("DEBIT", entries)
  const creditTotal = calculateTotal("CREDIT", entries)
  const editDebitTotal = calculateTotal("DEBIT", editEntries)
  const editCreditTotal = calculateTotal("CREDIT", editEntries)

  const renderEntryForm = (
    entryList: TransactionEntry[],
    type: "DEBIT" | "CREDIT",
    isEdit: boolean,
    total: number
  ) => {
    const getAccountCurrency = (accountId: string) => {
      const account = accounts.find(a => a.id === accountId)
      return account?.attributes.currency
    }

    return (
      <div className="border rounded-lg p-4 space-y-4">
        <div className="flex items-center justify-between">
          <h4 className="font-medium">{type === "DEBIT" ? t("transactions.debitEntries") : t("transactions.creditEntries")}</h4>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => addEntry(type, isEdit)}
          >
            <Plus className="h-4 w-4 mr-1" />
            {type === "DEBIT" ? t("transactions.addDebit") : t("transactions.addCredit")}
          </Button>
        </div>
        {entryList
          .map((entry, index) => ({ entry, index }))
          .filter(({ entry }) => entry.type === type)
          .map(({ entry, index }) => {
            const accountCurrency = getAccountCurrency(entry.accountId)
            const needsConversion = accountCurrency && entry.currency !== accountCurrency

            return (
              <div key={index} className="space-y-2">
                <div className="flex items-center gap-2">
                  <div className="flex-1">
                    <AccountAutocomplete
                      accounts={accounts}
                      value={entry.accountId}
                      onValueChange={(value) => updateEntry(index, "accountId", value, isEdit)}
                      placeholder={t("transactions.selectAccount")}
                    />
                  </div>
                  <Input
                    type="number"
                    step="0.01"
                    min="0"
                    value={entry.amount}
                    onChange={(e) => updateEntry(index, "amount", e.target.value, isEdit)}
                    onBlur={() => handleAmountBlur(isEdit)}
                    placeholder="0.00"
                    className="w-28"
                  />
                  <Input
                    type="text"
                    value={entry.currency}
                    onChange={(e) => updateEntry(index, "currency", e.target.value.toUpperCase(), isEdit)}
                    placeholder="USD"
                    maxLength={3}
                    className="w-20 uppercase"
                  />
                  {entryList.filter((e) => e.type === type).length > 1 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => removeEntry(index, isEdit)}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  )}
                </div>
                {needsConversion && (
                  <div className="pl-4 space-y-2">
                    <div className="text-sm text-muted-foreground">
                      → {t("transactions.accountCurrency")}: {accountCurrency}
                    </div>
                    <div className="flex items-center gap-2">
                      <Label className="text-xs text-muted-foreground w-24">{t("transactions.convertedTo")}:</Label>
                      <Input
                        type="number"
                        step="0.01"
                        min="0"
                        value={entry.toAmount || ""}
                        onChange={(e) => updateEntry(index, "toAmount", e.target.value, isEdit)}
                        placeholder={t("transactions.autoConversion")}
                        className="w-28 h-8 text-sm"
                      />
                      <Input
                        type="text"
                        value={entry.toCurrency || ""}
                        readOnly
                        placeholder={accountCurrency}
                        className="w-20 h-8 text-sm uppercase bg-muted"
                      />
                      <span className="text-xs text-muted-foreground">
                        ({t("transactions.manualConversion")})
                      </span>
                    </div>
                  </div>
                )}
                {/* Instrument selection (optional) */}
                {instruments.length > 0 && (
                  <div className="pl-4 space-y-2">
                    <div className="flex items-center gap-2">
                      <Label className="text-xs text-muted-foreground w-24">{t("transactions.instrument")}:</Label>
                      <Select
                        value={entry.instrumentId || "none"}
                        onValueChange={(value) => updateEntry(index, "instrumentId", value === "none" ? "" : value, isEdit)}
                      >
                        <SelectTrigger className="w-40 h-8 text-sm">
                          <SelectValue placeholder={t("transactions.selectInstrument")} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="none">{t("common.none")}</SelectItem>
                          {instruments.map((instrument) => (
                            <SelectItem key={instrument.id} value={instrument.id}>
                              {instrument.attributes.symbol}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      {entry.instrumentId && (
                        <Input
                          type="number"
                          step="0.00000001"
                          min="0"
                          value={entry.quantity || ""}
                          onChange={(e) => updateEntry(index, "quantity", e.target.value, isEdit)}
                          placeholder={t("transactions.quantity")}
                          className="w-28 h-8 text-sm"
                        />
                      )}
                    </div>
                  </div>
                )}
                {/* Envelope selection (optional) */}
                {envelopes.length > 0 && (
                  <div className="pl-4 space-y-2">
                    <div className="flex items-center gap-2">
                      <Label className="text-xs text-muted-foreground w-24">{t("transactions.envelope")}:</Label>
                      <Select
                        value={entry.envelopeId || "none"}
                        onValueChange={(value) => updateEntry(index, "envelopeId", value === "none" ? "" : value, isEdit)}
                      >
                        <SelectTrigger className="w-48 h-8 text-sm">
                          <SelectValue placeholder={t("transactions.selectEnvelope")} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="none">{t("common.none")}</SelectItem>
                          {envelopes.map((envelope) => (
                            <SelectItem key={envelope.id} value={envelope.id}>
                              {envelope.attributes.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        <div className="text-right text-sm font-medium">
          {t("transactions.total")}: {formatCurrency(total, selectedLedger?.attributes.currency || "BRL")}
        </div>
      </div>
    )
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

      {/* Create Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={(open) => {
        setIsCreateDialogOpen(open)
        if (!open) resetForm()
      }}>
        <DialogContent className="sm:max-w-[700px] lg:max-w-[900px] flex flex-col md:max-h-[90vh]">
          <DialogHeader>
            <DialogTitle>{t("transactions.createTransaction")}</DialogTitle>
            <DialogDescription>
              {t("transactions.createDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4 flex-1 min-h-0 overflow-y-auto">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="date" className="text-right">{t("common.date")}</Label>
              <Input
                id="date"
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="col-span-3"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="description" className="text-right">{t("common.description")}</Label>
              <div className="col-span-3">
                <DescriptionAutocomplete
                  templates={transactionTemplates}
                  value={description}
                  onValueChange={setDescription}
                  onTemplateSelect={(template) => handleTemplateSelect(template, false)}
                  placeholder={t("common.description")}
                />
              </div>
            </div>
            {renderEntryForm(entries, "CREDIT", false, creditTotal)}
            {renderEntryForm(entries, "DEBIT", false, debitTotal)}
            {!isBalanced(entries) && debitTotal > 0 && creditTotal > 0 && (
              <p className="text-sm text-destructive text-center">
                {t("transactions.notBalanced", { debits: formatCurrency(debitTotal, selectedLedger?.attributes.currency || "BRL"), credits: formatCurrency(creditTotal, selectedLedger?.attributes.currency || "BRL") })}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="submit"
              onClick={handleSubmit}
              disabled={!isFormValid(entries, date, description) || isSubmitting}
            >
              {isSubmitting ? t("transactions.creating") : t("transactions.createTransaction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog open={isEditDialogOpen} onOpenChange={(open) => {
        setIsEditDialogOpen(open)
        if (!open) setEditingTransaction(null)
      }}>
        <DialogContent className="sm:max-w-[700px] lg:max-w-[900px] flex flex-col md:max-h-[90vh]">
          <DialogHeader>
            <DialogTitle>{t("transactions.editTransaction")}</DialogTitle>
            <DialogDescription>
              {t("transactions.editDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4 flex-1 min-h-0 overflow-y-auto">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="edit-date" className="text-right">{t("common.date")}</Label>
              <Input
                id="edit-date"
                type="date"
                value={editDate}
                onChange={(e) => setEditDate(e.target.value)}
                className="col-span-3"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="edit-description" className="text-right">{t("common.description")}</Label>
              <div className="col-span-3">
                <DescriptionAutocomplete
                  templates={transactionTemplates}
                  value={editDescription}
                  onValueChange={setEditDescription}
                  onTemplateSelect={(template) => handleTemplateSelect(template, true)}
                  placeholder={t("common.description")}
                />
              </div>
            </div>
            {renderEntryForm(editEntries, "CREDIT", true, editCreditTotal)}
            {renderEntryForm(editEntries, "DEBIT", true, editDebitTotal)}
            {!isBalanced(editEntries) && editDebitTotal > 0 && editCreditTotal > 0 && (
              <p className="text-sm text-destructive text-center">
                {t("transactions.notBalanced", { debits: formatCurrency(editDebitTotal, selectedLedger?.attributes.currency || "BRL"), credits: formatCurrency(editCreditTotal, selectedLedger?.attributes.currency || "BRL") })}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="submit"
              onClick={handleUpdate}
              disabled={!isFormValid(editEntries, editDate, editDescription) || isSubmitting}
            >
              {isSubmitting ? t("transactions.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {ledgers.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {ledgers.map((ledger) => (
            <Button
              key={ledger.id}
              variant={selectedLedgerSlug === ledger.attributes.slug ? "default" : "outline"}
              size="sm"
              onClick={() => { setSelectedLedgerSlug(ledger.attributes.slug); setCurrentPage(0) }}
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
                          <Button variant="ghost" size="icon" className="h-8 w-8">
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
                          <Button variant="ghost" size="icon">
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
            {totalPages > 1 && (
              <div className="flex items-center justify-between pt-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
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
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-sm text-muted-foreground mr-2">
                    {t("transactions.page", { current: currentPage + 1, total: totalPages })}
                  </span>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage === 0} onClick={() => setCurrentPage(0)}>
                    <ChevronsLeft className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage === 0} onClick={() => setCurrentPage(currentPage - 1)}>
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(currentPage + 1)}>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(totalPages - 1)}>
                    <ChevronsRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
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
