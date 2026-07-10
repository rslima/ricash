import { useEffect, useState, useMemo } from "react"
import { useParams, Link } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { useAccounts, useCreateAccount, useUpdateAccount, useDeleteAccount } from "@/api/accounts.hooks"
import { ApiError } from "@/api/client"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useLedgers } from "@/api/ledgers.hooks"
import { useEnvelopes, useEnvelopeMappings, useSetEnvelopeAccounts } from "@/api/envelopes.hooks"
import { getEnvelopeAccounts } from "@/api/envelopes"
import type { AccountResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { Plus, Trash2, Wallet, MoreHorizontal, Pencil, ChevronRight, ChevronDown } from "lucide-react"
import { AccountAutocomplete } from "@/components/AccountAutocomplete"

const accountTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ASSET: "default",
  LIABILITY: "destructive",
  EQUITY: "secondary",
  INCOME: "default",
  EXPENSE: "destructive",
}

type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"

const ACCOUNT_TYPE_ORDER: AccountType[] = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]

interface AccountTreeNode {
  account: AccountResource
  children: AccountTreeNode[]
}

function buildAccountTree(accounts: AccountResource[]): AccountTreeNode[] {
  const accountMap = new Map<string, AccountTreeNode>()
  const roots: AccountTreeNode[] = []

  // Create nodes for all accounts
  accounts.forEach((account) => {
    accountMap.set(account.id, { account, children: [] })
  })

  // Build the tree structure
  accounts.forEach((account) => {
    const node = accountMap.get(account.id)!
    const parentId = account.attributes.parentAccountId

    if (parentId && accountMap.has(parentId)) {
      accountMap.get(parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

function countTreeNodes(nodes: AccountTreeNode[]): number {
  let count = 0
  for (const node of nodes) {
    count += 1 + countTreeNodes(node.children)
  }
  return count
}

interface AccountRowProps {
  node: AccountTreeNode
  depth: number
  expandedIds: Set<string>
  onToggleExpand: (id: string) => void
  onEdit: (account: AccountResource) => void
  onDelete: (accountId: string) => void
  onCreateChild: (account: AccountResource) => void
  ledgerSlug: string
}

function AccountRow({ node, depth, expandedIds, onToggleExpand, onEdit, onDelete, onCreateChild, ledgerSlug }: AccountRowProps) {
  const { t } = useTranslation()
  const { account, children } = node
  const hasChildren = children.length > 0
  const isExpanded = expandedIds.has(account.id)

  return (
    <>
      <TableRow>
        <TableCell className="font-medium">
          <div className="flex items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
            {hasChildren ? (
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6 p-0"
                onClick={() => onToggleExpand(account.id)}
              >
                {isExpanded ? (
                  <ChevronDown className="h-4 w-4" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )}
              </Button>
            ) : (
              <span className="w-6" />
            )}
            <Wallet className="h-4 w-4 text-muted-foreground" />
            <Link
              to={`/ledgers/${ledgerSlug}/accounts/${account.id}/transactions`}
              className="hover:underline hover:text-primary"
            >
              {account.attributes.name}
            </Link>
          </div>
        </TableCell>
        <TableCell>{account.attributes.currency}</TableCell>
        <TableCell className="text-right font-mono">
          {formatCurrency(account.attributes.balance, account.attributes.currency)}
        </TableCell>
        <TableCell>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => onCreateChild(account)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("accounts.createChildAccount")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onEdit(account)}>
                <Pencil className="mr-2 h-4 w-4" />
                {t("common.edit")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onDelete(account.id)}
                className="text-destructive"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {t("common.delete")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </TableCell>
      </TableRow>
      {isExpanded &&
        children.map((child) => (
          <AccountRow
            key={child.account.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggleExpand={onToggleExpand}
            onEdit={onEdit}
            onDelete={onDelete}
            onCreateChild={onCreateChild}
            ledgerSlug={ledgerSlug}
          />
        ))}
    </>
  )
}

export function Accounts() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState<AccountResource | null>(null)
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    currency: "BRL",
    type: "ASSET" as AccountType,
    parentAccountId: "",
    envelopeId: "",
  })
  const [editFormData, setEditFormData] = useState({
    name: "",
    description: "",
    type: "ASSET" as AccountType,
    currency: "BRL",
    parentAccountId: "",
    envelopeId: "",
  })

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const {
    data: ledgersResponse,
    isLoading: isLedgersLoading,
    isError: isLedgersError,
    error: ledgersError,
  } = useLedgers(isAuthenticated)
  const ledgers = useMemo(() => ledgersResponse?.data ?? [], [ledgersResponse])

  const {
    data: accountsResponse,
    isLoading: isAccountsLoading,
    isError: isAccountsError,
    error: accountsError,
  } = useAccounts(selectedLedgerSlug ?? "", { "page[size]": 200 }, isAuthenticated)
  const accounts = useMemo(() => accountsResponse?.data ?? [], [accountsResponse])

  const {
    data: envelopesResponse,
    isLoading: isEnvelopesLoading,
    isError: isEnvelopesError,
    error: envelopesError,
  } = useEnvelopes(selectedLedgerSlug ?? "", { "page[size]": 200 }, isAuthenticated)
  const envelopes = useMemo(() => envelopesResponse?.data ?? [], [envelopesResponse])

  const {
    data: envelopeMappingsData,
    isLoading: isMappingsLoading,
    isError: isMappingsError,
    error: mappingsError,
  } = useEnvelopeMappings(selectedLedgerSlug ?? "", isAuthenticated)
  const envelopeMappings = envelopeMappingsData ?? {}

  const createAccountMutation = useCreateAccount(selectedLedgerSlug ?? "")
  const updateAccountMutation = useUpdateAccount(selectedLedgerSlug ?? "")
  const deleteAccountMutation = useDeleteAccount(selectedLedgerSlug ?? "")
  const setEnvelopeAccountsMutation = useSetEnvelopeAccounts(selectedLedgerSlug ?? "")

  const isLoading = isLedgersLoading || isAccountsLoading || isEnvelopesLoading || isMappingsLoading
  const isCreating = createAccountMutation.isPending || setEnvelopeAccountsMutation.isPending
  const isUpdating = updateAccountMutation.isPending || setEnvelopeAccountsMutation.isPending

  // Combine query failures into a single fetch-error signal for the toast.
  const isError = isLedgersError || isAccountsError || isEnvelopesError || isMappingsError
  const error = ledgersError || accountsError || envelopesError || mappingsError

  const accountTree = useMemo(() => buildAccountTree(accounts), [accounts])

  // Group account trees by type
  const accountsByType = useMemo(() => {
    const grouped: Record<AccountType, AccountTreeNode[]> = {
      ASSET: [],
      LIABILITY: [],
      EQUITY: [],
      INCOME: [],
      EXPENSE: [],
    }

    accountTree.forEach((node) => {
      const type = node.account.attributes.type as AccountType
      if (grouped[type]) {
        grouped[type].push(node)
      }
    })

    return grouped
  }, [accountTree])

  // Filter valid parent accounts for edit (exclude self and descendants)
  const validParentAccountsForEdit = useMemo(() => {
    if (!editingAccount) return accounts

    const excludedIds = new Set<string>([editingAccount.id])
    const findDescendants = (id: string) => {
      accounts
        .filter((a) => a.attributes.parentAccountId === id)
        .forEach((child) => {
          excludedIds.add(child.id)
          findDescendants(child.id)
        })
    }
    findDescendants(editingAccount.id)

    return accounts.filter((a) => !excludedIds.has(a.id))
  }, [accounts, editingAccount])

  // Surface fetch failures as a toast (mutations report their own errors inline).
  useEffect(() => {
    if (isError) handleError(error, "fetchFailed")
  }, [isError, error, handleError])

  // Default to the first ledger once ledgers load (unless one is already chosen).
  useEffect(() => {
    if (ledgers.length > 0) {
      setSelectedLedgerSlug((prev) => prev ?? ledgers[0].attributes.slug)
    }
  }, [ledgers])

  // Expand all accounts by default whenever the account list (re)loads.
  useEffect(() => {
    setExpandedIds(new Set(accounts.map((a) => a.id)))
  }, [accounts])

  const handleToggleExpand = (id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const handleExpandAll = () => {
    setExpandedIds(new Set(accounts.map((a) => a.id)))
  }

  const handleCollapseAll = () => {
    setExpandedIds(new Set())
  }

  // Get all descendant IDs of an account (including the account itself)
  const getAllDescendantIds = (accountId: string): string[] => {
    const descendants: string[] = [accountId]
    const findDescendants = (id: string) => {
      accounts
        .filter((a) => a.attributes.parentAccountId === id)
        .forEach((child) => {
          descendants.push(child.id)
          findDescendants(child.id)
        })
    }
    findDescendants(accountId)
    return descendants
  }

  const handleDelete = (accountId: string) => {
    if (!selectedLedgerSlug) return

    const childCount = getAllDescendantIds(accountId).length - 1

    const message = childCount > 0
      ? t("accounts.confirmDeleteWithChildren", { count: childCount })
      : t("accounts.confirmDelete")

    if (!confirm(message)) return

    // The delete mutation invalidates the account cache, so the removed account
    // (and its descendants, cascaded server-side) drop out on the refetch.
    deleteAccountMutation.mutate(accountId, {
      onError: (error) => {
        if (error instanceof ApiError && error.status === 409) {
          handleError(error, "conflict")
        } else {
          handleError(error, "deleteFailed")
        }
      },
    })
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug) return

    try {
      const response = await createAccountMutation.mutateAsync({
        name: formData.name,
        description: formData.description || undefined,
        currency: formData.currency,
        type: formData.type,
        parentAccountId: formData.parentAccountId || undefined,
      })

      // Set envelope mapping if selected. The current membership is only known at
      // submit time, so we read it imperatively and write it via the mutation
      // hook (which invalidates the account/envelope caches on success).
      if (formData.envelopeId) {
        const currentAccounts = await getEnvelopeAccounts(selectedLedgerSlug, formData.envelopeId)
        await setEnvelopeAccountsMutation.mutateAsync({
          envelopeId: formData.envelopeId,
          accountIds: [...currentAccounts.accountIds, response.data.id],
        })
      }

      setIsCreateDialogOpen(false)
      setFormData({ name: "", description: "", currency: "BRL", type: "ASSET", parentAccountId: "", envelopeId: "" })
    } catch (error) {
      handleError(error, "createFailed")
    }
  }

  const handleCreateChild = (parentAccount: AccountResource) => {
    setFormData({
      name: "",
      description: "",
      currency: parentAccount.attributes.currency,
      type: parentAccount.attributes.type as AccountType,
      parentAccountId: parentAccount.id,
      envelopeId: "",
    })
    setIsCreateDialogOpen(true)
  }

  const handleEdit = (account: AccountResource) => {
    setEditingAccount(account)
    setEditFormData({
      name: account.attributes.name,
      description: account.attributes.description || "",
      type: account.attributes.type as AccountType,
      currency: account.attributes.currency,
      parentAccountId: account.attributes.parentAccountId || "",
      envelopeId: envelopeMappings[account.id] || "",
    })
    setIsEditDialogOpen(true)
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !editingAccount) return

    try {
      await updateAccountMutation.mutateAsync({
        accountId: editingAccount.id,
        data: {
          name: editFormData.name,
          description: editFormData.description || undefined,
          type: editFormData.type,
          currency: editFormData.currency,
          parentAccountId: editFormData.parentAccountId || null,
        },
      })

      // Update envelope mapping
      const currentEnvelopeId = envelopeMappings[editingAccount.id]
      const newEnvelopeId = editFormData.envelopeId

      if (currentEnvelopeId !== newEnvelopeId) {
        // Remove from old envelope
        if (currentEnvelopeId) {
          const oldEnvelopeAccounts = await getEnvelopeAccounts(selectedLedgerSlug, currentEnvelopeId)
          await setEnvelopeAccountsMutation.mutateAsync({
            envelopeId: currentEnvelopeId,
            accountIds: oldEnvelopeAccounts.accountIds.filter((id) => id !== editingAccount.id),
          })
        }

        // Add to new envelope
        if (newEnvelopeId) {
          const newEnvelopeAccounts = await getEnvelopeAccounts(selectedLedgerSlug, newEnvelopeId)
          await setEnvelopeAccountsMutation.mutateAsync({
            envelopeId: newEnvelopeId,
            accountIds: [...newEnvelopeAccounts.accountIds, editingAccount.id],
          })
        }
      }

      setIsEditDialogOpen(false)
      setEditingAccount(null)
    } catch (error) {
      handleError(error, "updateFailed")
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.accounts").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t("accounts.title")}</h2>
          <p className="text-muted-foreground">
            {t("accounts.subtitle")}
          </p>
        </div>
        <Button disabled={!selectedLedgerSlug} onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t("accounts.newAccount")}
        </Button>
      </div>

      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("accounts.createAccount")}</DialogTitle>
            <DialogDescription>
              {t("accounts.createDescription")}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="name">{t("common.name")}</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                  placeholder="Checking Account"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="type">{t("common.type")}</Label>
                <Select
                  value={formData.type}
                  onValueChange={(value: AccountType) =>
                    setFormData({ ...formData, type: value })
                  }
                  disabled={!!formData.parentAccountId}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.type")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ASSET">{t("accounts.types.ASSET")}</SelectItem>
                    <SelectItem value="LIABILITY">{t("accounts.types.LIABILITY")}</SelectItem>
                    <SelectItem value="EQUITY">{t("accounts.types.EQUITY")}</SelectItem>
                    <SelectItem value="INCOME">{t("accounts.types.INCOME")}</SelectItem>
                    <SelectItem value="EXPENSE">{t("accounts.types.EXPENSE")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="parentAccount">{t("accounts.parentAccount")} ({t("common.optional")})</Label>
                <AccountAutocomplete
                  accounts={accounts}
                  value={formData.parentAccountId}
                  onValueChange={(value) => {
                    if (value) {
                      const parentAccount = accounts.find((a) => a.id === value)
                      if (parentAccount) {
                        setFormData({
                          ...formData,
                          parentAccountId: value,
                          type: parentAccount.attributes.type as AccountType,
                          currency: parentAccount.attributes.currency,
                        })
                      }
                    } else {
                      setFormData({ ...formData, parentAccountId: "" })
                    }
                  }}
                  placeholder={t("accounts.parentAccount")}
                  allowNone
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="currency">{t("common.currency")}</Label>
                <Input
                  id="currency"
                  value={formData.currency}
                  onChange={(e) =>
                    setFormData({ ...formData, currency: e.target.value })
                  }
                  placeholder="USD"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="description">{t("common.description")} ({t("common.optional")})</Label>
                <Input
                  id="description"
                  value={formData.description}
                  onChange={(e) =>
                    setFormData({ ...formData, description: e.target.value })
                  }
                  placeholder="Main checking account for daily expenses"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="envelope">{t("transactions.envelope")} ({t("common.optional")})</Label>
                <Select
                  value={formData.envelopeId || "none"}
                  onValueChange={(value) =>
                    setFormData({ ...formData, envelopeId: value === "none" ? "" : value })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("transactions.selectEnvelope")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">{t("common.none")}</SelectItem>
                    {envelopes.map((envelope) => (
                      <SelectItem key={envelope.id} value={envelope.id}>
                        {envelope.attributes.name} ({t(`envelopes.types.${envelope.attributes.type}`)})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsCreateDialogOpen(false)}
              >
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={isCreating}>
                {isCreating ? t("accounts.creating") : t("accounts.createAccount")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("accounts.editAccount")}</DialogTitle>
            <DialogDescription>
              {t("accounts.editDescription")}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdate}>
            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="edit-name">{t("common.name")}</Label>
                <Input
                  id="edit-name"
                  value={editFormData.name}
                  onChange={(e) =>
                    setEditFormData({ ...editFormData, name: e.target.value })
                  }
                  placeholder="Checking Account"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-type">{t("common.type")}</Label>
                <Select
                  value={editFormData.type}
                  onValueChange={(value: AccountType) =>
                    setEditFormData({ ...editFormData, type: value })
                  }
                  disabled={!!editFormData.parentAccountId}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.type")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ASSET">{t("accounts.types.ASSET")}</SelectItem>
                    <SelectItem value="LIABILITY">{t("accounts.types.LIABILITY")}</SelectItem>
                    <SelectItem value="EQUITY">{t("accounts.types.EQUITY")}</SelectItem>
                    <SelectItem value="INCOME">{t("accounts.types.INCOME")}</SelectItem>
                    <SelectItem value="EXPENSE">{t("accounts.types.EXPENSE")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-parentAccount">{t("accounts.parentAccount")} ({t("common.optional")})</Label>
                <AccountAutocomplete
                  accounts={validParentAccountsForEdit}
                  value={editFormData.parentAccountId}
                  onValueChange={(value) => {
                    if (value) {
                      const parentAccount = accounts.find((a) => a.id === value)
                      if (parentAccount) {
                        setEditFormData({
                          ...editFormData,
                          parentAccountId: value,
                          type: parentAccount.attributes.type as AccountType,
                          currency: parentAccount.attributes.currency,
                        })
                      }
                    } else {
                      setEditFormData({ ...editFormData, parentAccountId: "" })
                    }
                  }}
                  placeholder={t("accounts.parentAccount")}
                  allowNone
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-currency">{t("common.currency")}</Label>
                <Input
                  id="edit-currency"
                  value={editFormData.currency}
                  onChange={(e) =>
                    setEditFormData({ ...editFormData, currency: e.target.value })
                  }
                  placeholder="BRL"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-description">{t("common.description")} ({t("common.optional")})</Label>
                <Input
                  id="edit-description"
                  value={editFormData.description}
                  onChange={(e) =>
                    setEditFormData({ ...editFormData, description: e.target.value })
                  }
                  placeholder="Main checking account for daily expenses"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-envelope">{t("transactions.envelope")} ({t("common.optional")})</Label>
                <Select
                  value={editFormData.envelopeId || "none"}
                  onValueChange={(value) =>
                    setEditFormData({ ...editFormData, envelopeId: value === "none" ? "" : value })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("transactions.selectEnvelope")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="none">{t("common.none")}</SelectItem>
                    {envelopes.map((envelope) => (
                      <SelectItem key={envelope.id} value={envelope.id}>
                        {envelope.attributes.name} ({t(`envelopes.types.${envelope.attributes.type}`)})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsEditDialogOpen(false)}
              >
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={isUpdating}>
                {isUpdating ? t("accounts.saving") : t("common.save")}
              </Button>
            </DialogFooter>
          </form>
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
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>
                {selectedLedger
                  ? `${t("accounts.title")} - ${selectedLedger.attributes.name}`
                  : t("accounts.noLedgerSelected")}
              </CardTitle>
              <CardDescription>
                {t("accounts.subtitle")}
              </CardDescription>
            </div>
            {accounts.length > 0 && (
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={handleExpandAll}>
                  {t("accounts.expandAll")}
                </Button>
                <Button variant="outline" size="sm" onClick={handleCollapseAll}>
                  {t("accounts.collapseAll")}
                </Button>
              </div>
            )}
          </div>
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
              <Wallet className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("accounts.noLedgerSelected")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("accounts.selectLedgerDescription")}
              </p>
              <Link to="/ledgers">
                <Button variant="outline">{t("accounts.goToLedgers")}</Button>
              </Link>
            </div>
          ) : accounts.length > 0 ? (
            <div className="space-y-6">
              {ACCOUNT_TYPE_ORDER.map((type) => {
                const typeAccounts = accountsByType[type]
                if (typeAccounts.length === 0) return null

                return (
                  <div key={type}>
                    <div className="flex items-center gap-2 mb-3">
                      <h3 className="text-lg font-semibold">{t(`accounts.typeGroups.${type}`)}</h3>
                      <Badge variant={accountTypeColors[type]}>{countTreeNodes(typeAccounts)}</Badge>
                    </div>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t("common.name")}</TableHead>
                          <TableHead>{t("common.currency")}</TableHead>
                          <TableHead className="text-right">{t("common.balance")}</TableHead>
                          <TableHead className="w-[70px]"></TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {typeAccounts.map((node) => (
                          <AccountRow
                            key={node.account.id}
                            node={node}
                            depth={0}
                            expandedIds={expandedIds}
                            onToggleExpand={handleToggleExpand}
                            onEdit={handleEdit}
                            onDelete={handleDelete}
                            onCreateChild={handleCreateChild}
                            ledgerSlug={selectedLedgerSlug!}
                          />
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <Wallet className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("accounts.noAccounts")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("accounts.noAccountsDescription")}
              </p>
              <Button onClick={() => setIsCreateDialogOpen(true)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("accounts.createAccount")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
