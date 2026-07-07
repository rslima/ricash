import { useEffect, useState, useMemo } from "react"
import { Link } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAuth } from "@/contexts/AuthContext"
import { useLedger } from "@/contexts/LedgerContext"
import { getAccounts, deleteAccount, createAccount, updateAccount } from "@/api/accounts"
import { ApiError } from "@/api/client"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useConfirm } from "@/components/ui/confirm-dialog"
import { getEnvelopes, getEnvelopeMappings, setEnvelopeAccounts, getEnvelopeAccounts } from "@/api/envelopes"
import type { AccountResource, EnvelopeResource } from "@/api/types"
import { Plus, Wallet } from "lucide-react"
import { AccountDialog, type AccountType } from "@/components/accounts/AccountDialog"
import { AccountRow, buildAccountTree, countTreeNodes, type AccountTreeNode } from "@/components/accounts/AccountTree"

const accountTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ASSET: "default",
  LIABILITY: "destructive",
  EQUITY: "secondary",
  INCOME: "default",
  EXPENSE: "destructive",
}

const ACCOUNT_TYPE_ORDER: AccountType[] = ["ASSET", "LIABILITY", "EQUITY", "INCOME", "EXPENSE"]

export function Accounts() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const confirm = useConfirm()
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [envelopes, setEnvelopes] = useState<EnvelopeResource[]>([])
  const [envelopeMappings, setEnvelopeMappings] = useState<Record<string, string>>({})
  const { ledgers, currentLedger, setCurrentLedger } = useLedger()
  const selectedLedgerSlug = currentLedger?.attributes.slug ?? null
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [isUpdating, setIsUpdating] = useState(false)
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

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      getAccounts(selectedLedgerSlug, { "page[size]": 200 }),
      getEnvelopes(selectedLedgerSlug, { "page[size]": 200 }),
      getEnvelopeMappings(selectedLedgerSlug),
    ])
      .then(([accountsResponse, envelopesResponse, mappingsResponse]) => {
        setAccounts(accountsResponse.data)
        setEnvelopes(envelopesResponse.data)
        setEnvelopeMappings(mappingsResponse)
        // Expand all accounts by default
        setExpandedIds(new Set(accountsResponse.data.map((a) => a.id)))
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated, handleError])

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

  const handleDelete = async (accountId: string) => {
    if (!selectedLedgerSlug) return

    const childCount = getAllDescendantIds(accountId).length - 1

    const message = childCount > 0
      ? t("accounts.confirmDeleteWithChildren", { count: childCount })
      : t("accounts.confirmDelete")

    if (!(await confirm({ description: message }))) return

    try {
      await deleteAccount(selectedLedgerSlug, accountId)
      // Remove the account and all its descendants from state
      const idsToRemove = new Set(getAllDescendantIds(accountId))
      setAccounts(accounts.filter((a) => !idsToRemove.has(a.id)))
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        handleError(error, "conflict")
      } else {
        handleError(error, "deleteFailed")
      }
    }
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug) return
    setIsCreating(true)

    try {
      const response = await createAccount(selectedLedgerSlug, {
        name: formData.name,
        description: formData.description || undefined,
        currency: formData.currency,
        type: formData.type,
        parentAccountId: formData.parentAccountId || undefined,
      })

      // Set envelope mapping if selected
      if (formData.envelopeId) {
        const currentAccounts = await getEnvelopeAccounts(selectedLedgerSlug, formData.envelopeId)
        await setEnvelopeAccounts(selectedLedgerSlug, formData.envelopeId, [...currentAccounts.accountIds, response.data.id])
        setEnvelopeMappings((prev) => ({ ...prev, [response.data.id]: formData.envelopeId }))
      }

      setAccounts([...accounts, response.data])
      setExpandedIds((prev) => new Set([...prev, response.data.id]))
      setIsCreateDialogOpen(false)
      setFormData({ name: "", description: "", currency: "BRL", type: "ASSET", parentAccountId: "", envelopeId: "" })
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsCreating(false)
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
    setIsUpdating(true)

    try {
      const response = await updateAccount(selectedLedgerSlug, editingAccount.id, {
        name: editFormData.name,
        description: editFormData.description || undefined,
        type: editFormData.type,
        currency: editFormData.currency,
        parentAccountId: editFormData.parentAccountId || null,
      })

      // Update envelope mapping
      const currentEnvelopeId = envelopeMappings[editingAccount.id]
      const newEnvelopeId = editFormData.envelopeId

      if (currentEnvelopeId !== newEnvelopeId) {
        // Remove from old envelope
        if (currentEnvelopeId) {
          const oldEnvelopeAccounts = await getEnvelopeAccounts(selectedLedgerSlug, currentEnvelopeId)
          await setEnvelopeAccounts(
            selectedLedgerSlug,
            currentEnvelopeId,
            oldEnvelopeAccounts.accountIds.filter((id) => id !== editingAccount.id)
          )
        }

        // Add to new envelope
        if (newEnvelopeId) {
          const newEnvelopeAccounts = await getEnvelopeAccounts(selectedLedgerSlug, newEnvelopeId)
          await setEnvelopeAccounts(selectedLedgerSlug, newEnvelopeId, [...newEnvelopeAccounts.accountIds, editingAccount.id])
        }

        // Update local state
        setEnvelopeMappings((prev) => {
          const updated = { ...prev }
          if (newEnvelopeId) {
            updated[editingAccount.id] = newEnvelopeId
          } else {
            delete updated[editingAccount.id]
          }
          return updated
        })
      }

      setAccounts(accounts.map((a) =>
        a.id === editingAccount.id ? response.data : a
      ))
      setIsEditDialogOpen(false)
      setEditingAccount(null)
    } catch (error) {
      handleError(error, "updateFailed")
    } finally {
      setIsUpdating(false)
    }
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

      <AccountDialog
        mode="create"
        open={isCreateDialogOpen}
        onOpenChange={setIsCreateDialogOpen}
        value={formData}
        onChange={setFormData}
        onSubmit={handleCreate}
        isSubmitting={isCreating}
        accounts={accounts}
        parentOptions={accounts}
        envelopes={envelopes}
      />

      <AccountDialog
        mode="edit"
        open={isEditDialogOpen}
        onOpenChange={setIsEditDialogOpen}
        value={editFormData}
        onChange={setEditFormData}
        onSubmit={handleUpdate}
        isSubmitting={isUpdating}
        accounts={accounts}
        parentOptions={validParentAccountsForEdit}
        envelopes={envelopes}
      />

      {ledgers.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {ledgers.map((ledger) => (
            <Button
              key={ledger.id}
              variant={selectedLedgerSlug === ledger.attributes.slug ? "default" : "outline"}
              size="sm"
              onClick={() => setCurrentLedger(ledger.attributes.slug)}
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
                            ledgerSlug={selectedLedgerSlug}
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
