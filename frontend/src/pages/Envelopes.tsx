import { useEffect, useState, useMemo } from "react"
import { useParams } from "react-router-dom"
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
import { getLedgers } from "@/api/ledgers"
import type { EnvelopeResource, LedgerResource, AccountResource, EnvelopeType, EnvelopeStatus } from "@/api/types"
import { Plus, Trash2, MoreHorizontal, Pencil, ChevronRight, ChevronDown, FolderOpen, Link as LinkIcon } from "lucide-react"
import { Checkbox } from "@/components/ui/checkbox"

const envelopeTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  EXPENSE: "destructive",
  INCOME: "default",
}

const ENVELOPE_TYPE_ORDER: EnvelopeType[] = ["INCOME", "EXPENSE"]

interface EnvelopeTreeNode {
  envelope: EnvelopeResource
  children: EnvelopeTreeNode[]
}

function buildEnvelopeTree(envelopes: EnvelopeResource[]): EnvelopeTreeNode[] {
  const envelopeMap = new Map<string, EnvelopeTreeNode>()
  const roots: EnvelopeTreeNode[] = []

  envelopes.forEach((envelope) => {
    envelopeMap.set(envelope.id, { envelope, children: [] })
  })

  envelopes.forEach((envelope) => {
    const node = envelopeMap.get(envelope.id)!
    const parentId = envelope.attributes.parentEnvelopeId

    if (parentId && envelopeMap.has(parentId)) {
      envelopeMap.get(parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

function countTreeNodes(nodes: EnvelopeTreeNode[]): number {
  let count = 0
  for (const node of nodes) {
    count += 1 + countTreeNodes(node.children)
  }
  return count
}

interface EnvelopeRowProps {
  node: EnvelopeTreeNode
  depth: number
  expandedIds: Set<string>
  onToggleExpand: (id: string) => void
  onEdit: (envelope: EnvelopeResource) => void
  onDelete: (envelopeId: string) => void
  onCreateChild: (envelope: EnvelopeResource) => void
  onManageAccounts: (envelope: EnvelopeResource) => void
  t: (key: string) => string
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
  t,
}: EnvelopeRowProps) {
  const { envelope, children } = node
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
              <Button variant="ghost" size="icon">
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
            key={child.envelope.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggleExpand={onToggleExpand}
            onEdit={onEdit}
            onDelete={onDelete}
            onCreateChild={onCreateChild}
            onManageAccounts={onManageAccounts}
            t={t}
          />
        ))}
    </>
  )
}

export function Envelopes() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [envelopes, setEnvelopes] = useState<EnvelopeResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
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
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    currency: "BRL",
    type: "EXPENSE" as EnvelopeType,
    parentEnvelopeId: "",
  })
  const [editFormData, setEditFormData] = useState({
    name: "",
    description: "",
    type: "EXPENSE" as EnvelopeType,
    currency: "BRL",
    status: "ACTIVE" as EnvelopeStatus,
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

    if (!confirm(message)) return

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
        status: editFormData.status,
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

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.envelopes").toLowerCase() })}
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
          <form onSubmit={handleCreate}>
            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="name">{t("common.name")}</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder={t("envelopes.namePlaceholder")}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="type">{t("common.type")}</Label>
                <Select
                  value={formData.type}
                  onValueChange={(value: EnvelopeType) => setFormData({ ...formData, type: value })}
                  disabled={!!formData.parentEnvelopeId}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.type")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EXPENSE">{t("envelopes.types.EXPENSE")}</SelectItem>
                    <SelectItem value="INCOME">{t("envelopes.types.INCOME")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="parentEnvelope">{t("envelopes.parentEnvelope")} ({t("common.optional")})</Label>
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
                <Label htmlFor="currency">{t("common.currency")}</Label>
                <Input
                  id="currency"
                  value={formData.currency}
                  onChange={(e) => setFormData({ ...formData, currency: e.target.value })}
                  placeholder="BRL"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="description">{t("common.description")} ({t("common.optional")})</Label>
                <Input
                  id="description"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  placeholder={t("envelopes.descriptionPlaceholder")}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateDialogOpen(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={isCreating}>
                {isCreating ? t("envelopes.creating") : t("envelopes.createEnvelope")}
              </Button>
            </DialogFooter>
          </form>
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
          <form onSubmit={handleUpdate}>
            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="edit-name">{t("common.name")}</Label>
                <Input
                  id="edit-name"
                  value={editFormData.name}
                  onChange={(e) => setEditFormData({ ...editFormData, name: e.target.value })}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-type">{t("common.type")}</Label>
                <Select
                  value={editFormData.type}
                  onValueChange={(value: EnvelopeType) => setEditFormData({ ...editFormData, type: value })}
                  disabled={!!editFormData.parentEnvelopeId}
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.type")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="EXPENSE">{t("envelopes.types.EXPENSE")}</SelectItem>
                    <SelectItem value="INCOME">{t("envelopes.types.INCOME")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-status">{t("common.status")}</Label>
                <Select
                  value={editFormData.status}
                  onValueChange={(value: EnvelopeStatus) => setEditFormData({ ...editFormData, status: value })}
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
              <div className="grid gap-2">
                <Label htmlFor="edit-parentEnvelope">{t("envelopes.parentEnvelope")} ({t("common.optional")})</Label>
                <Select
                  value={editFormData.parentEnvelopeId || "none"}
                  onValueChange={(value) => {
                    if (value === "none") {
                      setEditFormData({ ...editFormData, parentEnvelopeId: "" })
                    } else {
                      const parentEnvelope = envelopes.find((e) => e.id === value)
                      if (parentEnvelope) {
                        setEditFormData({
                          ...editFormData,
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
                    {validParentEnvelopesForEdit.map((envelope) => (
                      <SelectItem key={envelope.id} value={envelope.id}>
                        {envelope.attributes.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-currency">{t("common.currency")}</Label>
                <Input
                  id="edit-currency"
                  value={editFormData.currency}
                  onChange={(e) => setEditFormData({ ...editFormData, currency: e.target.value })}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-description">{t("common.description")} ({t("common.optional")})</Label>
                <Input
                  id="edit-description"
                  value={editFormData.description}
                  onChange={(e) => setEditFormData({ ...editFormData, description: e.target.value })}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsEditDialogOpen(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={isUpdating}>
                {isUpdating ? t("envelopes.saving") : t("common.save")}
              </Button>
            </DialogFooter>
          </form>
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
                            t={t}
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
