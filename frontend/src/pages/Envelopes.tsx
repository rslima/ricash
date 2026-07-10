import { useState, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
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
import { getEnvelopeAccounts } from "@/api/envelopes"
import { ApiError } from "@/api/client"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useLedgerSelection } from "@/hooks/use-ledger-selection"
import { combineQueries, useQueryErrorToast } from "@/hooks/use-query-error-toast"
import {
  useEnvelopes,
  useCreateEnvelope,
  useUpdateEnvelope,
  useDeleteEnvelope,
  useSetEnvelopeAccounts,
} from "@/api/envelopes.hooks"
import { useAccounts } from "@/api/accounts.hooks"
import { SignInRequired } from "@/components/SignInRequired"
import { EmptyState } from "@/components/EmptyState"
import { TableSkeleton } from "@/components/ui/table-skeleton"
import { LedgerSelector } from "@/components/LedgerSelector"
import { buildTree, countTreeNodes, collectDescendantIds, type TreeNode } from "@/lib/tree"
import { useExpandableTree } from "@/hooks/use-expandable-tree"
import type { EnvelopeResource, EnvelopeType, EnvelopeStatus } from "@/api/types"
import { Plus, Trash2, MoreHorizontal, Pencil, ChevronRight, ChevronDown, FolderOpen, Link as LinkIcon } from "lucide-react"
import { Checkbox } from "@/components/ui/checkbox"

const envelopeTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  EXPENSE: "destructive",
  INCOME: "default",
}

const ENVELOPE_TYPE_ORDER: EnvelopeType[] = ["INCOME", "EXPENSE"]

interface EnvelopeRowProps {
  node: TreeNode<EnvelopeResource>
  depth: number
  expandedIds: Set<string>
  onToggleExpand: (id: string) => void
  onEdit: (envelope: EnvelopeResource) => void
  onDelete: (envelopeId: string) => void
  onCreateChild: (envelope: EnvelopeResource) => void
  onManageAccounts: (envelope: EnvelopeResource) => void
}

function EnvelopeRow({
  node,
  depth,
  expandedIds,
  onToggleExpand,
  onEdit,
  onDelete,
  onCreateChild,
  onManageAccounts,
}: EnvelopeRowProps) {
  const { t } = useTranslation()
  const { item: envelope, children } = node
  const hasChildren = children.length > 0
  const isExpanded = expandedIds.has(envelope.id)
  const isArchived = envelope.attributes.status === "ARCHIVED"

  return (
    <>
      <TableRow className={isArchived ? "opacity-50" : ""}>
        <TableCell className="font-medium">
          <div className="flex items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
            {hasChildren ? (
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6 p-0"
                aria-label={isExpanded ? t("common.collapse") : t("common.expand")}
                onClick={() => onToggleExpand(envelope.id)}
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
            <FolderOpen className="h-4 w-4 text-muted-foreground" />
            <span>{envelope.attributes.name}</span>
            {isArchived && (
              <Badge variant="outline" className="ml-2">
                {t("envelopes.archived")}
              </Badge>
            )}
          </div>
        </TableCell>
        <TableCell>{envelope.attributes.currency}</TableCell>
        <TableCell>
          <Badge variant={envelopeTypeColors[envelope.attributes.type]}>
            {t(`envelopes.types.${envelope.attributes.type}`)}
          </Badge>
        </TableCell>
        <TableCell>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" aria-label={t("common.actions")}>
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => onCreateChild(envelope)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("envelopes.createChildEnvelope")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onManageAccounts(envelope)}>
                <LinkIcon className="mr-2 h-4 w-4" />
                {t("envelopes.manageAccounts")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onEdit(envelope)}>
                <Pencil className="mr-2 h-4 w-4" />
                {t("common.edit")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onDelete(envelope.id)}
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
          <EnvelopeRow
            key={child.item.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggleExpand={onToggleExpand}
            onEdit={onEdit}
            onDelete={onDelete}
            onCreateChild={onCreateChild}
            onManageAccounts={onManageAccounts}
          />
        ))}
    </>
  )
}

interface EnvelopeFormData {
  name: string
  description: string
  currency: string
  type: EnvelopeType
  status: EnvelopeStatus
  parentEnvelopeId: string
}

interface EnvelopeFormProps {
  idPrefix: string
  formData: EnvelopeFormData
  setFormData: (data: EnvelopeFormData) => void
  envelopes: EnvelopeResource[]
  onSubmit: (e: React.FormEvent) => void
  onCancel: () => void
  submitLabel: string
  submittingLabel: string
  isSubmitting: boolean
  showStatus?: boolean
  showPlaceholders?: boolean
}

function EnvelopeForm({
  idPrefix,
  formData,
  setFormData,
  envelopes,
  onSubmit,
  onCancel,
  submitLabel,
  submittingLabel,
  isSubmitting,
  showStatus = false,
  showPlaceholders = false,
}: EnvelopeFormProps) {
  const { t } = useTranslation()

  return (
    <form onSubmit={onSubmit}>
      <div className="grid gap-4 py-4">
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}name`}>{t("common.name")}</Label>
          <Input
            id={`${idPrefix}name`}
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder={showPlaceholders ? t("envelopes.namePlaceholder") : undefined}
            required
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}type`}>{t("common.type")}</Label>
          <Select
            value={formData.type}
            onValueChange={(value: EnvelopeType) => setFormData({ ...formData, type: value })}
            disabled={!!formData.parentEnvelopeId}
          >
            <SelectTrigger>
              <SelectValue placeholder={t("common.type")} />
            </SelectTrigger>
            <SelectContent>
              {ENVELOPE_TYPE_ORDER.map((type) => (
                <SelectItem key={type} value={type}>
                  {t(`envelopes.types.${type}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        {showStatus && (
          <div className="grid gap-2">
            <Label htmlFor={`${idPrefix}status`}>{t("common.status")}</Label>
            <Select
              value={formData.status}
              onValueChange={(value: EnvelopeStatus) => setFormData({ ...formData, status: value })}
            >
              <SelectTrigger>
                <SelectValue placeholder={t("common.status")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACTIVE">{t("envelopes.statuses.ACTIVE")}</SelectItem>
                <SelectItem value="ARCHIVED">{t("envelopes.statuses.ARCHIVED")}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}parentEnvelope`}>{t("envelopes.parentEnvelope")} ({t("common.optional")})</Label>
          <Select
            value={formData.parentEnvelopeId || "none"}
            onValueChange={(value) => {
              if (value === "none") {
                setFormData({ ...formData, parentEnvelopeId: "" })
              } else {
                const parentEnvelope = envelopes.find((e) => e.id === value)
                if (parentEnvelope) {
                  setFormData({
                    ...formData,
                    parentEnvelopeId: value,
                    type: parentEnvelope.attributes.type,
                    currency: parentEnvelope.attributes.currency,
                  })
                }
              }
            }}
          >
            <SelectTrigger>
              <SelectValue placeholder={t("envelopes.parentEnvelope")} />
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
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}currency`}>{t("common.currency")}</Label>
          <Input
            id={`${idPrefix}currency`}
            value={formData.currency}
            onChange={(e) => setFormData({ ...formData, currency: e.target.value })}
            placeholder={showPlaceholders ? "BRL" : undefined}
            required
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor={`${idPrefix}description`}>{t("common.description")} ({t("common.optional")})</Label>
          <Input
            id={`${idPrefix}description`}
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            placeholder={showPlaceholders ? t("envelopes.descriptionPlaceholder") : undefined}
          />
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

export function Envelopes() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isAccountsDialogOpen, setIsAccountsDialogOpen] = useState(false)
  const [editingEnvelope, setEditingEnvelope] = useState<EnvelopeResource | null>(null)
  const [managingEnvelope, setManagingEnvelope] = useState<EnvelopeResource | null>(null)
  const [selectedAccountIds, setSelectedAccountIds] = useState<string[]>([])
  const [formData, setFormData] = useState<EnvelopeFormData>({
    name: "",
    description: "",
    currency: "BRL",
    type: "EXPENSE",
    status: "ACTIVE",
    parentEnvelopeId: "",
  })
  const [editFormData, setEditFormData] = useState<EnvelopeFormData>({
    name: "",
    description: "",
    type: "EXPENSE",
    currency: "BRL",
    status: "ACTIVE",
    parentEnvelopeId: "",
  })

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const ledgerSelection = useLedgerSelection()
  const { ledgers, selectedLedgerSlug, setSelectedLedgerSlug, selectedLedger } = ledgerSelection

  const envelopesQuery = useEnvelopes(
    selectedLedgerSlug ?? "",
    { "page[size]": 200 },
    isAuthenticated && !!selectedLedgerSlug
  )
  const envelopes = useMemo(() => envelopesQuery.data?.data ?? [], [envelopesQuery.data])

  const accountsQuery = useAccounts(
    selectedLedgerSlug ?? "",
    { "page[size]": 200 },
    isAuthenticated && !!selectedLedgerSlug
  )
  const accounts = accountsQuery.data?.data ?? []

  const createEnvelopeMutation = useCreateEnvelope(selectedLedgerSlug ?? "")
  const updateEnvelopeMutation = useUpdateEnvelope(selectedLedgerSlug ?? "")
  const deleteEnvelopeMutation = useDeleteEnvelope(selectedLedgerSlug ?? "")
  const setEnvelopeAccountsMutation = useSetEnvelopeAccounts(selectedLedgerSlug ?? "")

  const { isLoading } = combineQueries(ledgerSelection, envelopesQuery, accountsQuery)

  // Expands all envelopes whenever a fresh list arrives (mirrors prior fetch behavior).
  const { expandedIds, toggle, expandAll, collapseAll } = useExpandableTree(envelopes)

  const envelopeTree = useMemo(
    () => buildTree(envelopes, (e) => e.id, (e) => e.attributes.parentEnvelopeId),
    [envelopes]
  )

  const envelopesByType = useMemo(() => {
    const grouped: Record<EnvelopeType, TreeNode<EnvelopeResource>[]> = {
      INCOME: [],
      EXPENSE: [],
    }

    envelopeTree.forEach((node) => {
      const type = node.item.attributes.type
      if (grouped[type]) {
        grouped[type].push(node)
      }
    })

    return grouped
  }, [envelopeTree])

  const validParentEnvelopesForEdit = useMemo(() => {
    if (!editingEnvelope) return envelopes

    const excludedIds = new Set(
      collectDescendantIds(envelopes, editingEnvelope.id, (e) => e.id, (e) => e.attributes.parentEnvelopeId)
    )

    return envelopes.filter((e) => !excludedIds.has(e.id))
  }, [envelopes, editingEnvelope])

  // Surface fetch failures as a toast (mutations report their own errors inline).
  useQueryErrorToast([ledgerSelection, envelopesQuery, accountsQuery])

  const handleDelete = (envelopeId: string) => {
    if (!selectedLedgerSlug) return

    const childCount =
      collectDescendantIds(envelopes, envelopeId, (e) => e.id, (e) => e.attributes.parentEnvelopeId).length - 1

    const message = childCount > 0
      ? t("envelopes.confirmDeleteWithChildren", { count: childCount })
      : t("envelopes.confirmDelete")

    if (!confirm(message)) return

    deleteEnvelopeMutation.mutate(envelopeId, {
      onError: (error) => {
        if (error instanceof ApiError && error.status === 409) {
          handleError(error, "conflict")
        } else {
          handleError(error, "deleteFailed")
        }
      },
    })
  }

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug) return

    createEnvelopeMutation.mutate(
      {
        name: formData.name,
        description: formData.description || undefined,
        currency: formData.currency,
        type: formData.type,
        parentEnvelopeId: formData.parentEnvelopeId || undefined,
      },
      {
        onSuccess: () => {
          setIsCreateDialogOpen(false)
          setFormData({ name: "", description: "", currency: "BRL", type: "EXPENSE", status: "ACTIVE", parentEnvelopeId: "" })
        },
        onError: (error) => handleError(error, "createFailed"),
      }
    )
  }

  const handleCreateChild = (parentEnvelope: EnvelopeResource) => {
    setFormData({
      name: "",
      description: "",
      currency: parentEnvelope.attributes.currency,
      type: parentEnvelope.attributes.type,
      status: "ACTIVE",
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

  const handleUpdate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !editingEnvelope) return

    updateEnvelopeMutation.mutate(
      {
        envelopeId: editingEnvelope.id,
        data: {
          name: editFormData.name,
          description: editFormData.description || undefined,
          type: editFormData.type,
          currency: editFormData.currency,
          status: editFormData.status,
          parentEnvelopeId: editFormData.parentEnvelopeId || null,
        },
      },
      {
        onSuccess: () => {
          setIsEditDialogOpen(false)
          setEditingEnvelope(null)
        },
        onError: (error) => handleError(error, "updateFailed"),
      }
    )
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

  const handleSaveAccounts = () => {
    if (!selectedLedgerSlug || !managingEnvelope) return

    setEnvelopeAccountsMutation.mutate(
      { envelopeId: managingEnvelope.id, accountIds: selectedAccountIds },
      {
        onSuccess: () => {
          setIsAccountsDialogOpen(false)
          setManagingEnvelope(null)
        },
        onError: (error) => handleError(error, "updateFailed"),
      }
    )
  }

  const toggleAccountSelection = (accountId: string) => {
    setSelectedAccountIds((prev) =>
      prev.includes(accountId)
        ? prev.filter((id) => id !== accountId)
        : [...prev, accountId]
    )
  }

  if (!isAuthenticated) {
    return <SignInRequired resourceKey="nav.envelopes" />
  }

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

      {/* Create Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("envelopes.createEnvelope")}</DialogTitle>
            <DialogDescription>
              {t("envelopes.createDescription")}
            </DialogDescription>
          </DialogHeader>
          <EnvelopeForm
            idPrefix=""
            formData={formData}
            setFormData={setFormData}
            envelopes={envelopes}
            onSubmit={handleCreate}
            onCancel={() => setIsCreateDialogOpen(false)}
            submitLabel={t("envelopes.createEnvelope")}
            submittingLabel={t("envelopes.creating")}
            isSubmitting={createEnvelopeMutation.isPending}
            showPlaceholders
          />
        </DialogContent>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("envelopes.editEnvelope")}</DialogTitle>
            <DialogDescription>
              {t("envelopes.editDescription")}
            </DialogDescription>
          </DialogHeader>
          <EnvelopeForm
            idPrefix="edit-"
            formData={editFormData}
            setFormData={setEditFormData}
            envelopes={validParentEnvelopesForEdit}
            onSubmit={handleUpdate}
            onCancel={() => setIsEditDialogOpen(false)}
            submitLabel={t("common.save")}
            submittingLabel={t("envelopes.saving")}
            isSubmitting={updateEnvelopeMutation.isPending}
            showStatus
          />
        </DialogContent>
      </Dialog>

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
            <Button onClick={handleSaveAccounts} disabled={setEnvelopeAccountsMutation.isPending}>
              {setEnvelopeAccountsMutation.isPending ? t("common.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <LedgerSelector
        ledgers={ledgers}
        selectedSlug={selectedLedgerSlug}
        onSelect={setSelectedLedgerSlug}
      />

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
                <Button variant="outline" size="sm" onClick={expandAll}>
                  {t("envelopes.expandAll")}
                </Button>
                <Button variant="outline" size="sm" onClick={collapseAll}>
                  {t("envelopes.collapseAll")}
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
              icon={FolderOpen}
              title={t("envelopes.noLedgerSelected")}
              description={t("envelopes.selectLedgerDescription")}
            />
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
                            key={node.item.id}
                            node={node}
                            depth={0}
                            expandedIds={expandedIds}
                            onToggleExpand={toggle}
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
            <EmptyState
              icon={FolderOpen}
              title={t("envelopes.noEnvelopes")}
              description={t("envelopes.noEnvelopesDescription")}
              action={
                <Button onClick={() => setIsCreateDialogOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t("envelopes.createEnvelope")}
                </Button>
              }
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}
