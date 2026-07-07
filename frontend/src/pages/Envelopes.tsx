import { useEffect, useState, useMemo } from "react"
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { useAuth } from "@/contexts/AuthContext"
import { useLedger } from "@/contexts/LedgerContext"
import {
  getEnvelopes,
  deleteEnvelope,
  createEnvelope,
  updateEnvelope,
  getEnvelopeAccounts,
  setEnvelopeAccounts,
} from "@/api/envelopes"
import { getAccounts } from "@/api/accounts"
import { ApiError } from "@/api/client"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useConfirm } from "@/components/ui/confirm-dialog"
import type { EnvelopeResource, AccountResource, EnvelopeType } from "@/api/types"
import { Plus, FolderOpen } from "lucide-react"
import { Checkbox } from "@/components/ui/checkbox"
import { EnvelopeDialog, type EnvelopeFormValues } from "@/components/envelopes/EnvelopeDialog"
import { EnvelopeRow, buildEnvelopeTree, countTreeNodes, envelopeTypeColors, type EnvelopeTreeNode } from "@/components/envelopes/EnvelopeTree"

const ENVELOPE_TYPE_ORDER: EnvelopeType[] = ["INCOME", "EXPENSE"]

export function Envelopes() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const confirm = useConfirm()
  const [envelopes, setEnvelopes] = useState<EnvelopeResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const { ledgers, currentLedger, setCurrentLedger } = useLedger()
  const selectedLedgerSlug = currentLedger?.attributes.slug ?? null
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isAccountsDialogOpen, setIsAccountsDialogOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [isUpdating, setIsUpdating] = useState(false)
  const [isSavingAccounts, setIsSavingAccounts] = useState(false)
  const [editingEnvelope, setEditingEnvelope] = useState<EnvelopeResource | null>(null)
  const [managingEnvelope, setManagingEnvelope] = useState<EnvelopeResource | null>(null)
  const [selectedAccountIds, setSelectedAccountIds] = useState<string[]>([])
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())
  const [formData, setFormData] = useState<EnvelopeFormValues>({
    name: "",
    description: "",
    currency: "BRL",
    type: "EXPENSE",
    parentEnvelopeId: "",
  })
  const [editFormData, setEditFormData] = useState<EnvelopeFormValues>({
    name: "",
    description: "",
    type: "EXPENSE",
    currency: "BRL",
    status: "ACTIVE",
    parentEnvelopeId: "",
  })

  const envelopeTree = useMemo(() => buildEnvelopeTree(envelopes), [envelopes])

  const envelopesByType = useMemo(() => {
    const grouped: Record<EnvelopeType, EnvelopeTreeNode[]> = {
      INCOME: [],
      EXPENSE: [],
    }

    envelopeTree.forEach((node) => {
      const type = node.envelope.attributes.type
      if (grouped[type]) {
        grouped[type].push(node)
      }
    })

    return grouped
  }, [envelopeTree])

  const validParentEnvelopesForEdit = useMemo(() => {
    if (!editingEnvelope) return envelopes

    const excludedIds = new Set<string>([editingEnvelope.id])
    const findDescendants = (id: string) => {
      envelopes
        .filter((e) => e.attributes.parentEnvelopeId === id)
        .forEach((child) => {
          excludedIds.add(child.id)
          findDescendants(child.id)
        })
    }
    findDescendants(editingEnvelope.id)

    return envelopes.filter((e) => !excludedIds.has(e.id))
  }, [envelopes, editingEnvelope])

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      getEnvelopes(selectedLedgerSlug, { "page[size]": 200 }),
      getAccounts(selectedLedgerSlug, { "page[size]": 200 }),
    ])
      .then(([envelopesResponse, accountsResponse]) => {
        setEnvelopes(envelopesResponse.data)
        setAccounts(accountsResponse.data)
        setExpandedIds(new Set(envelopesResponse.data.map((e) => e.id)))
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
    setExpandedIds(new Set(envelopes.map((e) => e.id)))
  }

  const handleCollapseAll = () => {
    setExpandedIds(new Set())
  }

  const getAllDescendantIds = (envelopeId: string): string[] => {
    const descendants: string[] = [envelopeId]
    const findDescendants = (id: string) => {
      envelopes
        .filter((e) => e.attributes.parentEnvelopeId === id)
        .forEach((child) => {
          descendants.push(child.id)
          findDescendants(child.id)
        })
    }
    findDescendants(envelopeId)
    return descendants
  }

  const handleDelete = async (envelopeId: string) => {
    if (!selectedLedgerSlug) return

    const childCount = getAllDescendantIds(envelopeId).length - 1

    const message = childCount > 0
      ? t("envelopes.confirmDeleteWithChildren", { count: childCount })
      : t("envelopes.confirmDelete")

    if (!(await confirm({ description: message }))) return

    try {
      await deleteEnvelope(selectedLedgerSlug, envelopeId)
      const idsToRemove = new Set(getAllDescendantIds(envelopeId))
      setEnvelopes(envelopes.filter((e) => !idsToRemove.has(e.id)))
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
      const response = await createEnvelope(selectedLedgerSlug, {
        name: formData.name,
        description: formData.description || undefined,
        currency: formData.currency,
        type: formData.type,
        parentEnvelopeId: formData.parentEnvelopeId || undefined,
      })
      setEnvelopes([...envelopes, response.data])
      setExpandedIds((prev) => new Set([...prev, response.data.id]))
      setIsCreateDialogOpen(false)
      setFormData({ name: "", description: "", currency: "BRL", type: "EXPENSE", parentEnvelopeId: "" })
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsCreating(false)
    }
  }

  const handleCreateChild = (parentEnvelope: EnvelopeResource) => {
    setFormData({
      name: "",
      description: "",
      currency: parentEnvelope.attributes.currency,
      type: parentEnvelope.attributes.type,
      parentEnvelopeId: parentEnvelope.id,
    })
    setIsCreateDialogOpen(true)
  }

  const handleEdit = (envelope: EnvelopeResource) => {
    setEditingEnvelope(envelope)
    setEditFormData({
      name: envelope.attributes.name,
      description: envelope.attributes.description || "",
      type: envelope.attributes.type,
      currency: envelope.attributes.currency,
      status: envelope.attributes.status,
      parentEnvelopeId: envelope.attributes.parentEnvelopeId || "",
    })
    setIsEditDialogOpen(true)
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !editingEnvelope) return
    setIsUpdating(true)

    try {
      const response = await updateEnvelope(selectedLedgerSlug, editingEnvelope.id, {
        name: editFormData.name,
        description: editFormData.description || undefined,
        type: editFormData.type,
        currency: editFormData.currency,
        status: editFormData.status ?? "ACTIVE",
        parentEnvelopeId: editFormData.parentEnvelopeId || null,
      })
      setEnvelopes(envelopes.map((e) =>
        e.id === editingEnvelope.id ? response.data : e
      ))
      setIsEditDialogOpen(false)
      setEditingEnvelope(null)
    } catch (error) {
      handleError(error, "updateFailed")
    } finally {
      setIsUpdating(false)
    }
  }

  const handleManageAccounts = async (envelope: EnvelopeResource) => {
    if (!selectedLedgerSlug) return
    setManagingEnvelope(envelope)

    try {
      const response = await getEnvelopeAccounts(selectedLedgerSlug, envelope.id)
      setSelectedAccountIds(response.accountIds)
      setIsAccountsDialogOpen(true)
    } catch (error) {
      handleError(error, "fetchFailed")
    }
  }

  const handleSaveAccounts = async () => {
    if (!selectedLedgerSlug || !managingEnvelope) return
    setIsSavingAccounts(true)

    try {
      await setEnvelopeAccounts(selectedLedgerSlug, managingEnvelope.id, selectedAccountIds)
      setIsAccountsDialogOpen(false)
      setManagingEnvelope(null)
    } catch (error) {
      handleError(error, "updateFailed")
    } finally {
      setIsSavingAccounts(false)
    }
  }

  const toggleAccountSelection = (accountId: string) => {
    setSelectedAccountIds((prev) =>
      prev.includes(accountId)
        ? prev.filter((id) => id !== accountId)
        : [...prev, accountId]
    )
  }


  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t("envelopes.title")}</h2>
          <p className="text-muted-foreground">
            {t("envelopes.subtitle")}
          </p>
        </div>
        <Button disabled={!selectedLedgerSlug} onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t("envelopes.newEnvelope")}
        </Button>
      </div>

      <EnvelopeDialog
        mode="create"
        open={isCreateDialogOpen}
        onOpenChange={setIsCreateDialogOpen}
        value={formData}
        onChange={setFormData}
        onSubmit={handleCreate}
        isSubmitting={isCreating}
        envelopes={envelopes}
        parentOptions={envelopes}
      />

      <EnvelopeDialog
        mode="edit"
        open={isEditDialogOpen}
        onOpenChange={setIsEditDialogOpen}
        value={editFormData}
        onChange={setEditFormData}
        onSubmit={handleUpdate}
        isSubmitting={isUpdating}
        envelopes={envelopes}
        parentOptions={validParentEnvelopesForEdit}
      />

      {/* Manage Accounts Dialog */}
      <Dialog open={isAccountsDialogOpen} onOpenChange={setIsAccountsDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("envelopes.manageAccountsFor", { name: managingEnvelope?.attributes.name })}</DialogTitle>
            <DialogDescription>
              {t("envelopes.manageAccountsDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-96 overflow-y-auto py-4">
            <div className="space-y-2">
              {accounts.map((account) => (
                <div key={account.id} className="flex items-center space-x-2">
                  <Checkbox
                    id={account.id}
                    checked={selectedAccountIds.includes(account.id)}
                    onCheckedChange={() => toggleAccountSelection(account.id)}
                  />
                  <label
                    htmlFor={account.id}
                    className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer"
                  >
                    {account.attributes.name}
                    <span className="ml-2 text-muted-foreground">
                      ({account.attributes.type} - {account.attributes.currency})
                    </span>
                  </label>
                </div>
              ))}
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setIsAccountsDialogOpen(false)}>
              {t("common.cancel")}
            </Button>
            <Button onClick={handleSaveAccounts} disabled={isSavingAccounts}>
              {isSavingAccounts ? t("common.saving") : t("common.save")}
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
                  ? `${t("envelopes.title")} - ${selectedLedger.attributes.name}`
                  : t("envelopes.noLedgerSelected")}
              </CardTitle>
              <CardDescription>
                {t("envelopes.subtitle")}
              </CardDescription>
            </div>
            {envelopes.length > 0 && (
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={handleExpandAll}>
                  {t("envelopes.expandAll")}
                </Button>
                <Button variant="outline" size="sm" onClick={handleCollapseAll}>
                  {t("envelopes.collapseAll")}
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
              <FolderOpen className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("envelopes.noLedgerSelected")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("envelopes.selectLedgerDescription")}
              </p>
            </div>
          ) : envelopes.length > 0 ? (
            <div className="space-y-6">
              {ENVELOPE_TYPE_ORDER.map((type) => {
                const typeEnvelopes = envelopesByType[type]
                if (typeEnvelopes.length === 0) return null

                return (
                  <div key={type}>
                    <div className="flex items-center gap-2 mb-3">
                      <h3 className="text-lg font-semibold">{t(`envelopes.typeGroups.${type}`)}</h3>
                      <Badge variant={envelopeTypeColors[type]}>{countTreeNodes(typeEnvelopes)}</Badge>
                    </div>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t("common.name")}</TableHead>
                          <TableHead>{t("common.currency")}</TableHead>
                          <TableHead>{t("common.type")}</TableHead>
                          <TableHead className="w-[70px]"></TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {typeEnvelopes.map((node) => (
                          <EnvelopeRow
                            key={node.envelope.id}
                            node={node}
                            depth={0}
                            expandedIds={expandedIds}
                            onToggleExpand={handleToggleExpand}
                            onEdit={handleEdit}
                            onDelete={handleDelete}
                            onCreateChild={handleCreateChild}
                            onManageAccounts={handleManageAccounts}
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
              <FolderOpen className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("envelopes.noEnvelopes")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("envelopes.noEnvelopesDescription")}
              </p>
              <Button onClick={() => setIsCreateDialogOpen(true)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("envelopes.createEnvelope")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
