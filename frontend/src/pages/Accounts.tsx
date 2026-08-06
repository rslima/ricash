import { useState, useMemo } from "react"
import { Link } from "react-router"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { TableSkeleton } from "@/components/ui/table-skeleton"
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
import { useLedgerSelection } from "@/hooks/use-ledger-selection"
import { combineQueries, useQueryErrorToast } from "@/hooks/use-query-error-toast"
import { useEnvelopes, useEnvelopeMappings, useSetEnvelopeAccounts } from "@/api/envelopes.hooks"
import { getEnvelopeAccounts } from "@/api/envelopes"
import type { AccountResource, EnvelopeResource } from "@/api/types"
import { buildTree, countTreeNodes, collectDescendantIds, type TreeNode } from "@/lib/tree"
import { useExpandableTree } from "@/hooks/use-expandable-tree"
import { formatCurrency } from "@/lib/utils"
import { Plus, Trash2, Wallet, MoreHorizontal, Pencil, ChevronRight, ChevronDown } from "lucide-react"
import { AccountAutocomplete } from "@/components/AccountAutocomplete"
import { SignInRequired } from "@/components/SignInRequired"
import { EmptyState } from "@/components/EmptyState"
import { LedgerSelector } from "@/components/LedgerSelector"

const accountTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ASSET: "default",
  LIABILITY: "destructive",
  EQUITY: "secondary",
  INCOME: "default",
  EXPENSE: "destructive",
}

type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"

const ACCOUNT_TYPE_ORDER: AccountType[] = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]

interface AccountFormData {
  name: string
  description: string
  currency: string
  type: AccountType
  parentAccountId: string
  envelopeId: string
}

interface AccountRowProps {
  node: TreeNode<AccountResource>
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
  const { item: account, children } = node
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
                aria-label={isExpanded ? t("common.collapse") : t("common.expand")}
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
              <Button variant="ghost" size="icon" aria-label={t("common.actions")}>
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
            key={child.item.id}
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

interface AccountFormProps {
  idPrefix: string
  value: AccountFormData
  onChange: (value: AccountFormData) => void
  /** Parent-account options: all accounts on create, the non-descendant subset on edit. */
  accounts: AccountResource[]
  envelopes: EnvelopeResource[]
  onSubmit: (e: React.FormEvent) => void
  onCancel: () => void
  submitLabel: string
  submittingLabel: string
  isSubmitting: boolean
  currencyPlaceholder: string
}

function AccountForm({
  idPrefix,
  value,
  onChange,
  accounts,
  envelopes,
  onSubmit,
  onCancel,
  submitLabel,
  submittingLabel,
  isSubmitting,
  currencyPlaceholder,
}: AccountFormProps) {
  const { t } = useTranslation()

  const handleParentChange = (parentAccountId: string) => {
    if (parentAccountId) {
      const parentAccount = accounts.find((a) => a.id === parentAccountId)
      if (parentAccount) {
        onChange({
          ...value,
          parentAccountId,
          type: parentAccount.attributes.type as AccountType,
          currency: parentAccount.attributes.currency,
        })
      }
    } else {
      onChange({ ...value, parentAccountId: "" })
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <div className="grid gap-4 py-4">
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}name`}>{t("common.name")}</Label>
          <Input
            id={`${idPrefix}name`}
            value={value.name}
            onChange={(e) => onChange({ ...value, name: e.target.value })}
            placeholder="Checking Account"
            required
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}type`}>{t("common.type")}</Label>
          <Select
            value={value.type}
            onValueChange={(type: AccountType) => onChange({ ...value, type })}
            disabled={!!value.parentAccountId}
          >
            <SelectTrigger>
              <SelectValue placeholder={t("common.type")} />
            </SelectTrigger>
            <SelectContent>
              {ACCOUNT_TYPE_ORDER.map((type) => (
                <SelectItem key={type} value={type}>
                  {t(`accounts.types.${type}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}parentAccount`}>{t("accounts.parentAccount")} ({t("common.optional")})</Label>
          <AccountAutocomplete
            accounts={accounts}
            value={value.parentAccountId}
            onValueChange={handleParentChange}
            placeholder={t("accounts.parentAccount")}
            allowNone
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}currency`}>{t("common.currency")}</Label>
          <Input
            id={`${idPrefix}currency`}
            value={value.currency}
            onChange={(e) => onChange({ ...value, currency: e.target.value })}
            placeholder={currencyPlaceholder}
            required
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}description`}>{t("common.description")} ({t("common.optional")})</Label>
          <Input
            id={`${idPrefix}description`}
            value={value.description}
            onChange={(e) => onChange({ ...value, description: e.target.value })}
            placeholder="Main checking account for daily expenses"
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}envelope`}>{t("transactions.envelope")} ({t("common.optional")})</Label>
          <Select
            value={value.envelopeId || "none"}
            onValueChange={(envelopeId) =>
              onChange({ ...value, envelopeId: envelopeId === "none" ? "" : envelopeId })
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
        <Button type="button" variant="outline" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? submittingLabel : submitLabel}
        </Button>
      </DialogFooter>
    </form>
  )
}

export function Accounts() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const ledgerSelection = useLedgerSelection()
  const { ledgers, selectedLedgerSlug, setSelectedLedgerSlug, selectedLedger } = ledgerSelection
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [editingAccount, setEditingAccount] = useState<AccountResource | null>(null)
  const [formData, setFormData] = useState<AccountFormData>({
    name: "",
    description: "",
    currency: "BRL",
    type: "ASSET",
    parentAccountId: "",
    envelopeId: "",
  })
  const [editFormData, setEditFormData] = useState<AccountFormData>({
    name: "",
    description: "",
    type: "ASSET",
    currency: "BRL",
    parentAccountId: "",
    envelopeId: "",
  })

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const accountsQuery = useAccounts(selectedLedgerSlug ?? "", { "page[size]": 200 }, isAuthenticated)
  const accounts = useMemo(() => accountsQuery.data?.data ?? [], [accountsQuery.data])

  const envelopesQuery = useEnvelopes(selectedLedgerSlug ?? "", { "page[size]": 200 }, isAuthenticated)
  const envelopes = useMemo(() => envelopesQuery.data?.data ?? [], [envelopesQuery.data])

  const mappingsQuery = useEnvelopeMappings(selectedLedgerSlug ?? "", isAuthenticated)
  const envelopeMappings = mappingsQuery.data ?? {}

  const createAccountMutation = useCreateAccount(selectedLedgerSlug ?? "")
  const updateAccountMutation = useUpdateAccount(selectedLedgerSlug ?? "")
  const deleteAccountMutation = useDeleteAccount(selectedLedgerSlug ?? "")
  const setEnvelopeAccountsMutation = useSetEnvelopeAccounts(selectedLedgerSlug ?? "")

  const { isLoading } = combineQueries(ledgerSelection, accountsQuery, envelopesQuery, mappingsQuery)
  const isCreating = createAccountMutation.isPending || setEnvelopeAccountsMutation.isPending
  const isUpdating = updateAccountMutation.isPending || setEnvelopeAccountsMutation.isPending

  // Surface fetch failures as a toast (mutations report their own errors inline).
  useQueryErrorToast([ledgerSelection, accountsQuery, envelopesQuery, mappingsQuery])

  const { expandedIds, toggle, expandAll, collapseAll } = useExpandableTree(accounts)

  const accountTree = useMemo(
    () => buildTree(accounts, (a) => a.id, (a) => a.attributes.parentAccountId),
    [accounts]
  )

  // Group account trees by type
  const accountsByType = useMemo(() => {
    const grouped: Record<AccountType, TreeNode<AccountResource>[]> = {
      ASSET: [],
      LIABILITY: [],
      EQUITY: [],
      INCOME: [],
      EXPENSE: [],
    }

    accountTree.forEach((node) => {
      const type = node.item.attributes.type as AccountType
      if (grouped[type]) {
        grouped[type].push(node)
      }
    })

    return grouped
  }, [accountTree])

  // Filter valid parent accounts for edit (exclude self and descendants)
  const validParentAccountsForEdit = useMemo(() => {
    if (!editingAccount) return accounts

    const excludedIds = new Set(
      collectDescendantIds(accounts, editingAccount.id, (a) => a.id, (a) => a.attributes.parentAccountId)
    )

    return accounts.filter((a) => !excludedIds.has(a.id))
  }, [accounts, editingAccount])

  const handleDelete = (accountId: string) => {
    if (!selectedLedgerSlug) return

    const childCount =
      collectDescendantIds(accounts, accountId, (a) => a.id, (a) => a.attributes.parentAccountId).length - 1

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
    return <SignInRequired resourceKey="nav.accounts" />
  }

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
          <AccountForm
            idPrefix=""
            value={formData}
            onChange={setFormData}
            accounts={accounts}
            envelopes={envelopes}
            onSubmit={handleCreate}
            onCancel={() => setIsCreateDialogOpen(false)}
            submitLabel={t("accounts.createAccount")}
            submittingLabel={t("accounts.creating")}
            isSubmitting={isCreating}
            currencyPlaceholder="USD"
          />
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
          <AccountForm
            idPrefix="edit-"
            value={editFormData}
            onChange={setEditFormData}
            accounts={validParentAccountsForEdit}
            envelopes={envelopes}
            onSubmit={handleUpdate}
            onCancel={() => setIsEditDialogOpen(false)}
            submitLabel={t("common.save")}
            submittingLabel={t("accounts.saving")}
            isSubmitting={isUpdating}
            currencyPlaceholder="BRL"
          />
        </DialogContent>
      </Dialog>

      <LedgerSelector ledgers={ledgers} selectedSlug={selectedLedgerSlug} onSelect={setSelectedLedgerSlug} />

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
                <Button variant="outline" size="sm" onClick={expandAll}>
                  {t("accounts.expandAll")}
                </Button>
                <Button variant="outline" size="sm" onClick={collapseAll}>
                  {t("accounts.collapseAll")}
                </Button>
              </div>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <TableSkeleton />
          ) : !selectedLedgerSlug ? (
            <EmptyState
              icon={Wallet}
              title={t("accounts.noLedgerSelected")}
              description={t("accounts.selectLedgerDescription")}
              action={
                <Link to="/ledgers">
                  <Button variant="outline">{t("accounts.goToLedgers")}</Button>
                </Link>
              }
            />
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
                            key={node.item.id}
                            node={node}
                            depth={0}
                            expandedIds={expandedIds}
                            onToggleExpand={toggle}
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
            <EmptyState
              icon={Wallet}
              title={t("accounts.noAccounts")}
              description={t("accounts.noAccountsDescription")}
              action={
                <Button onClick={() => setIsCreateDialogOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t("accounts.createAccount")}
                </Button>
              }
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
