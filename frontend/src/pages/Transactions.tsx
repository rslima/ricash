import { useEffect, useState } from "react"
import { useParams, Link } from "react-router-dom"
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
import { getTransactions, deleteTransaction, createTransaction, updateTransaction, type TransactionEntryInput } from "@/api/transactions"
import { getLedgers } from "@/api/ledgers"
import { getAccounts } from "@/api/accounts"
import type { TransactionResource, LedgerResource, AccountResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { Plus, Trash2, ArrowLeftRight, MoreHorizontal, X, Pencil } from "lucide-react"

interface TransactionEntry {
  accountId: string
  amount: string
  type: "DEBIT" | "CREDIT"
}

export function Transactions() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [editingTransaction, setEditingTransaction] = useState<TransactionResource | null>(null)

  // Form state
  const [date, setDate] = useState(new Date().toISOString().split("T")[0])
  const [description, setDescription] = useState("")
  const [entries, setEntries] = useState<TransactionEntry[]>([
    { accountId: "", amount: "", type: "CREDIT" },
    { accountId: "", amount: "", type: "DEBIT" },
  ])

  // Edit form state
  const [editDate, setEditDate] = useState("")
  const [editDescription, setEditDescription] = useState("")
  const [editEntries, setEditEntries] = useState<TransactionEntry[]>([])

  useEffect(() => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    getLedgers()
      .then((response) => {
        setLedgers(response.data)
        if (!selectedLedgerSlug && response.data.length > 0) {
          setSelectedLedgerSlug(response.data[0].attributes.slug)
        }
      })
      .catch(console.error)
  }, [isAuthenticated])

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      getTransactions(selectedLedgerSlug),
      getAccounts(selectedLedgerSlug),
    ])
      .then(([transactionsResponse, accountsResponse]) => {
        setTransactions(transactionsResponse.data)
        setAccounts(accountsResponse.data)
      })
      .catch(console.error)
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated])

  const handleDelete = async (transactionId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("transactions.confirmDelete"))) return

    try {
      await deleteTransaction(selectedLedgerSlug, transactionId)
      setTransactions(transactions.filter((t) => t.id !== transactionId))
    } catch (error) {
      console.error("Failed to delete transaction:", error)
    }
  }

  const resetForm = () => {
    setDate(new Date().toISOString().split("T")[0])
    setDescription("")
    setEntries([
      { accountId: "", amount: "", type: "CREDIT" },
      { accountId: "", amount: "", type: "DEBIT" },
    ])
  }

  const addEntry = (type: "DEBIT" | "CREDIT", isEdit = false) => {
    if (isEdit) {
      setEditEntries([...editEntries, { accountId: "", amount: "", type }])
    } else {
      setEntries([...entries, { accountId: "", amount: "", type }])
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
      setEditEntries(newEntries)
    } else {
      const newEntries = [...entries]
      newEntries[index] = { ...newEntries[index], [field]: value }
      setEntries(newEntries)
    }
  }

  const calculateTotal = (type: "DEBIT" | "CREDIT", entryList: TransactionEntry[]) => {
    return entryList
      .filter((e) => e.type === type)
      .reduce((sum, e) => sum + (parseFloat(e.amount) || 0), 0)
  }

  const isBalanced = (entryList: TransactionEntry[]) => {
    const debits = calculateTotal("DEBIT", entryList)
    const credits = calculateTotal("CREDIT", entryList)
    return Math.abs(debits - credits) < 0.01 && debits > 0
  }

  const isFormValid = (entryList: TransactionEntry[], dateValue: string, descValue: string) => {
    return (
      dateValue &&
      descValue.trim() &&
      entryList.every((e) => e.accountId && parseFloat(e.amount) > 0) &&
      isBalanced(entryList)
    )
  }

  const handleSubmit = async () => {
    if (!selectedLedgerSlug || !isFormValid(entries, date, description)) return

    setIsSubmitting(true)
    try {
      const transactionEntries: TransactionEntryInput[] = entries.map((e) => ({
        accountId: e.accountId,
        amount: parseFloat(e.amount),
        type: e.type,
      }))

      const response = await createTransaction(selectedLedgerSlug, {
        date,
        description,
        entries: transactionEntries,
      })

      setTransactions([response.data, ...transactions])
      setIsCreateDialogOpen(false)
      resetForm()
    } catch (error) {
      console.error("Failed to create transaction:", error)
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEdit = (transaction: TransactionResource) => {
    setEditingTransaction(transaction)
    setEditDate(transaction.attributes.date)
    setEditDescription(transaction.attributes.description)

    // Convert entries from the transaction
    const transactionEntries: TransactionEntry[] = transaction.attributes.entries?.map((entry) => ({
      accountId: entry.accountId,
      amount: entry.amount.toString(),
      type: entry.type,
    })) || []

    setEditEntries(transactionEntries.length > 0 ? transactionEntries : [
      { accountId: "", amount: "", type: "CREDIT" },
      { accountId: "", amount: "", type: "DEBIT" },
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
        type: e.type,
      }))

      const response = await updateTransaction(selectedLedgerSlug, editingTransaction.id, {
        date: editDate,
        description: editDescription,
        entries: transactionEntries,
      })

      setTransactions(transactions.map((t) =>
        t.id === editingTransaction.id ? response.data : t
      ))
      setIsEditDialogOpen(false)
      setEditingTransaction(null)
    } catch (error) {
      console.error("Failed to update transaction:", error)
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

  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)
  const debitTotal = calculateTotal("DEBIT", entries)
  const creditTotal = calculateTotal("CREDIT", entries)
  const editDebitTotal = calculateTotal("DEBIT", editEntries)
  const editCreditTotal = calculateTotal("CREDIT", editEntries)

  const renderEntryForm = (
    entryList: TransactionEntry[],
    type: "DEBIT" | "CREDIT",
    isEdit: boolean,
    total: number
  ) => (
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
        .map(({ entry, index }) => (
          <div key={index} className="flex items-center gap-2">
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
              placeholder="0.00"
              className="w-32"
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
        ))}
      <div className="text-right text-sm font-medium">
        {t("transactions.total")}: {formatCurrency(total, selectedLedger?.attributes.currency || "BRL")}
      </div>
    </div>
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t("transactions.title")}</h2>
          <p className="text-muted-foreground">
            {t("transactions.subtitle")}
          </p>
        </div>
        <Button
          disabled={!selectedLedgerSlug || accounts.length === 0}
          onClick={() => setIsCreateDialogOpen(true)}
        >
          <Plus className="mr-2 h-4 w-4" />
          {t("transactions.newTransaction")}
        </Button>
      </div>

      {/* Create Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={(open) => {
        setIsCreateDialogOpen(open)
        if (!open) resetForm()
      }}>
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader>
            <DialogTitle>{t("transactions.createTransaction")}</DialogTitle>
            <DialogDescription>
              {t("transactions.createDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
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
              <Input
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={t("common.description")}
                className="col-span-3"
              />
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
        <DialogContent className="sm:max-w-[600px]">
          <DialogHeader>
            <DialogTitle>{t("transactions.editTransaction")}</DialogTitle>
            <DialogDescription>
              {t("transactions.editDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
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
              <Input
                id="edit-description"
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                placeholder={t("common.description")}
                className="col-span-3"
              />
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
              onClick={() => setSelectedLedgerSlug(ledger.attributes.slug)}
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
                            {entry.accountName}: {entry.type === "DEBIT" ? "DR" : "CR"}
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
