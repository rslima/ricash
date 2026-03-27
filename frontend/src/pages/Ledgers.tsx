import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
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
import { useAuth } from "@/contexts/AuthContext"
import { getLedgers, deleteLedger, createLedger, updateLedger } from "@/api/ledgers"
import type { LedgerResource } from "@/api/types"
import { formatDate } from "@/lib/utils"
import { useIsMobile } from "@/hooks/use-mobile"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { Plus, Trash2, BookOpen, MoreHorizontal, Pencil } from "lucide-react"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

export function Ledgers() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const isMobile = useIsMobile()
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [isUpdating, setIsUpdating] = useState(false)
  const [editingLedger, setEditingLedger] = useState<LedgerResource | null>(null)
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    currency: "BRL",
  })
  const [editFormData, setEditFormData] = useState({
    name: "",
    description: "",
  })

  const fetchLedgers = async () => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    try {
      const response = await getLedgers()
      setLedgers(response.data)
    } catch (error) {
      handleError(error, "fetchFailed")
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchLedgers()
  }, [isAuthenticated])

  const handleDelete = async (slug: string) => {
    if (!confirm(t("ledgers.confirmDelete"))) return

    try {
      await deleteLedger(slug)
      setLedgers(ledgers.filter((l) => l.attributes.slug !== slug))
    } catch (error) {
      handleError(error, "deleteFailed")
    }
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsCreating(true)

    try {
      const response = await createLedger({
        name: formData.name,
        description: formData.description || undefined,
        currency: formData.currency,
      })
      setLedgers([...ledgers, response.data])
      setIsCreateDialogOpen(false)
      setFormData({ name: "", description: "", currency: "BRL" })
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsCreating(false)
    }
  }

  const handleEdit = (ledger: LedgerResource) => {
    setEditingLedger(ledger)
    setEditFormData({
      name: ledger.attributes.name,
      description: ledger.attributes.description || "",
    })
    setIsEditDialogOpen(true)
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!editingLedger) return
    setIsUpdating(true)

    try {
      const response = await updateLedger(editingLedger.attributes.slug, {
        name: editFormData.name,
        description: editFormData.description || undefined,
      })
      setLedgers(ledgers.map((l) =>
        l.id === editingLedger.id ? response.data : l
      ))
      setIsEditDialogOpen(false)
      setEditingLedger(null)
    } catch (error) {
      handleError(error, "updateFailed")
    } finally {
      setIsUpdating(false)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.ledgers").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-4 md:space-y-6">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="text-xl md:text-2xl font-bold tracking-tight">{t("ledgers.title")}</h2>
          <p className="text-sm text-muted-foreground">
            {t("ledgers.subtitle")}
          </p>
        </div>
        <Button size={isMobile ? "sm" : "default"} onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-1 md:mr-2 h-4 w-4" />
          {isMobile ? t("common.create") : t("ledgers.newLedger")}
        </Button>
      </div>

      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("ledgers.createLedger")}</DialogTitle>
            <DialogDescription>
              {t("ledgers.createDescription")}
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
                  placeholder="Personal Finance"
                  required
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
                  placeholder="Track personal income and expenses"
                />
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
                {isCreating ? t("ledgers.creating") : t("ledgers.createLedger")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("ledgers.editLedger")}</DialogTitle>
            <DialogDescription>
              {t("ledgers.editDescription")}
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
                  placeholder="Personal Finance"
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
                  placeholder="Track personal income and expenses"
                />
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
                {isUpdating ? t("ledgers.saving") : t("common.save")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Card>
        <CardHeader>
          <CardTitle>{t("ledgers.title")}</CardTitle>
          <CardDescription>
            {t("ledgers.createNew")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : ledgers.length > 0 ? (
            isMobile ? (
            <div className="space-y-2">
              {ledgers.map((ledger) => (
                <div key={ledger.id} className="rounded-lg border bg-card p-3">
                  <div className="flex items-start justify-between">
                    <Link
                      to={`/ledgers/${ledger.attributes.slug}/accounts`}
                      className="min-w-0 flex-1"
                    >
                      <div className="flex items-center gap-2">
                        <BookOpen className="h-4 w-4 text-muted-foreground shrink-0" />
                        <span className="font-medium truncate">{ledger.attributes.name}</span>
                        <Badge variant="secondary" className="shrink-0">{ledger.attributes.currency}</Badge>
                      </div>
                      {ledger.attributes.description && (
                        <p className="text-xs text-muted-foreground mt-1 truncate pl-6">
                          {ledger.attributes.description}
                        </p>
                      )}
                    </Link>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8 shrink-0">
                          <MoreHorizontal className="h-4 w-4" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => handleEdit(ledger)}>
                          <Pencil className="mr-2 h-4 w-4" />
                          {t("common.edit")}
                        </DropdownMenuItem>
                        <DropdownMenuItem
                          onClick={() => handleDelete(ledger.attributes.slug)}
                          className="text-destructive"
                        >
                          <Trash2 className="mr-2 h-4 w-4" />
                          {t("common.delete")}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </div>
              ))}
            </div>
            ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.name")}</TableHead>
                  <TableHead>{t("common.currency")}</TableHead>
                  <TableHead>{t("common.description")}</TableHead>
                  <TableHead>{t("common.date")}</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {ledgers.map((ledger) => (
                  <TableRow key={ledger.id}>
                    <TableCell>
                      <Link
                        to={`/ledgers/${ledger.attributes.slug}/accounts`}
                        className="font-medium hover:underline flex items-center gap-2"
                      >
                        <BookOpen className="h-4 w-4 text-muted-foreground" />
                        {ledger.attributes.name}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {ledger.attributes.currency}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {ledger.attributes.description || "—"}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(ledger.attributes.createdAt)}
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleEdit(ledger)}>
                            <Pencil className="mr-2 h-4 w-4" />
                            {t("common.edit")}
                          </DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => handleDelete(ledger.attributes.slug)}
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
            )
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <BookOpen className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("ledgers.noLedgers")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("ledgers.noLedgersDescription")}
              </p>
              <Button onClick={() => setIsCreateDialogOpen(true)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("ledgers.createLedger")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
