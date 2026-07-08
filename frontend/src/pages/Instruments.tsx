import { useEffect, useState } from "react"
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
import { getInstruments, createInstrument, updateInstrument, deleteInstrument } from "@/api/instruments"
import { getLedgers } from "@/api/ledgers"
import type { InstrumentResource, LedgerResource, InstrumentType, InstrumentStatus } from "@/api/types"
import { Plus, Trash2, TrendingUp, MoreHorizontal, Pencil, Briefcase } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"

const instrumentTypeColors: Record<InstrumentType, "default" | "secondary" | "destructive" | "outline"> = {
  STOCK: "default",
  ETF: "secondary",
  TREASURY_BOND: "outline",
  FIXED_INCOME: "outline",
  FUND: "secondary",
}

export function Instruments() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [instruments, setInstruments] = useState<InstrumentResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [editingInstrument, setEditingInstrument] = useState<InstrumentResource | null>(null)

  // Form state
  const [formData, setFormData] = useState({
    symbol: "",
    name: "",
    type: "STOCK" as InstrumentType,
    currency: "BRL",
    market: "",
    isin: "",
  })

  const [editFormData, setEditFormData] = useState({
    symbol: "",
    name: "",
    type: "STOCK" as InstrumentType,
    currency: "BRL",
    market: "",
    isin: "",
    status: "ACTIVE" as InstrumentStatus,
  })

  const resetForm = () => {
    setFormData({
      symbol: "",
      name: "",
      type: "STOCK",
      currency: "BRL",
      market: "",
      isin: "",
    })
  }

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
    getInstruments(selectedLedgerSlug, { "page[size]": 100 })
      .then((response) => {
        setInstruments(response.data)
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated, handleError])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug) return

    setIsSubmitting(true)
    try {
      const response = await createInstrument(selectedLedgerSlug, {
        symbol: formData.symbol.toUpperCase(),
        name: formData.name,
        type: formData.type,
        currency: formData.currency.toUpperCase(),
        market: formData.market || undefined,
        isin: formData.isin || undefined,
      })
      setInstruments([...instruments, response.data])
      setIsCreateDialogOpen(false)
      resetForm()
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleEdit = (instrument: InstrumentResource) => {
    setEditingInstrument(instrument)
    setEditFormData({
      symbol: instrument.attributes.symbol,
      name: instrument.attributes.name,
      type: instrument.attributes.type,
      currency: instrument.attributes.currency,
      market: instrument.attributes.market || "",
      isin: instrument.attributes.isin || "",
      status: instrument.attributes.status,
    })
    setIsEditDialogOpen(true)
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !editingInstrument) return

    setIsSubmitting(true)
    try {
      const response = await updateInstrument(selectedLedgerSlug, editingInstrument.id, {
        symbol: editFormData.symbol.toUpperCase(),
        name: editFormData.name,
        type: editFormData.type,
        currency: editFormData.currency.toUpperCase(),
        market: editFormData.market || undefined,
        isin: editFormData.isin || undefined,
        status: editFormData.status,
      })
      setInstruments(instruments.map((i) => (i.id === editingInstrument.id ? response.data : i)))
      setIsEditDialogOpen(false)
      setEditingInstrument(null)
    } catch (error) {
      handleError(error, "updateFailed")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDelete = async (instrumentId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("instruments.confirmDelete"))) return

    try {
      await deleteInstrument(selectedLedgerSlug, instrumentId)
      setInstruments(instruments.filter((i) => i.id !== instrumentId))
    } catch (error) {
      handleError(error, "deleteFailed")
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.instruments").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t("instruments.title")}</h1>
          <p className="text-muted-foreground">{t("instruments.description")}</p>
        </div>
        <Button disabled={!selectedLedgerSlug} onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t("instruments.newInstrument")}
        </Button>
      </div>

      {/* Create Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={(open) => {
        setIsCreateDialogOpen(open)
        if (!open) resetForm()
      }}>
        <DialogContent className="sm:max-w-[500px]">
          <DialogHeader>
            <DialogTitle>{t("instruments.createInstrument")}</DialogTitle>
            <DialogDescription>
              {t("instruments.createDescription")}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="symbol" className="text-right">{t("instruments.symbol")}</Label>
                <Input
                  id="symbol"
                  value={formData.symbol}
                  onChange={(e) => setFormData({ ...formData, symbol: e.target.value.toUpperCase() })}
                  placeholder="PETR4"
                  className="col-span-3 uppercase"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="name" className="text-right">{t("common.name")}</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder="Petrobras PN"
                  className="col-span-3"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="type" className="text-right">{t("common.type")}</Label>
                <Select value={formData.type} onValueChange={(value: InstrumentType) => setFormData({ ...formData, type: value })}>
                  <SelectTrigger className="col-span-3">
                    <SelectValue placeholder={t("common.type")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="STOCK">{t("instruments.types.STOCK")}</SelectItem>
                    <SelectItem value="ETF">{t("instruments.types.ETF")}</SelectItem>
                    <SelectItem value="TREASURY_BOND">{t("instruments.types.TREASURY_BOND")}</SelectItem>
                    <SelectItem value="FIXED_INCOME">{t("instruments.types.FIXED_INCOME")}</SelectItem>
                    <SelectItem value="FUND">{t("instruments.types.FUND")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="currency" className="text-right">{t("common.currency")}</Label>
                <Input
                  id="currency"
                  value={formData.currency}
                  onChange={(e) => setFormData({ ...formData, currency: e.target.value.toUpperCase() })}
                  placeholder="BRL"
                  maxLength={3}
                  className="col-span-3 uppercase"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="market" className="text-right">{t("instruments.market")} ({t("common.optional")})</Label>
                <Input
                  id="market"
                  value={formData.market}
                  onChange={(e) => setFormData({ ...formData, market: e.target.value })}
                  placeholder="B3"
                  className="col-span-3"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="isin" className="text-right">ISIN ({t("common.optional")})</Label>
                <Input
                  id="isin"
                  value={formData.isin}
                  onChange={(e) => setFormData({ ...formData, isin: e.target.value.toUpperCase() })}
                  placeholder="BRPETRACNPR6"
                  maxLength={12}
                  className="col-span-3 uppercase"
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateDialogOpen(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={isSubmitting || !formData.symbol || !formData.name}>
                {isSubmitting ? t("instruments.creating") : t("common.create")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Edit Dialog */}
      <Dialog open={isEditDialogOpen} onOpenChange={(open) => {
        setIsEditDialogOpen(open)
        if (!open) setEditingInstrument(null)
      }}>
        <DialogContent className="sm:max-w-[500px]">
          <DialogHeader>
            <DialogTitle>{t("instruments.editInstrument")}</DialogTitle>
            <DialogDescription>
              {t("instruments.editDescription")}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdate}>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-symbol" className="text-right">{t("instruments.symbol")}</Label>
                <Input
                  id="edit-symbol"
                  value={editFormData.symbol}
                  onChange={(e) => setEditFormData({ ...editFormData, symbol: e.target.value.toUpperCase() })}
                  placeholder="PETR4"
                  className="col-span-3 uppercase"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-name" className="text-right">{t("common.name")}</Label>
                <Input
                  id="edit-name"
                  value={editFormData.name}
                  onChange={(e) => setEditFormData({ ...editFormData, name: e.target.value })}
                  placeholder="Petrobras PN"
                  className="col-span-3"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-type" className="text-right">{t("common.type")}</Label>
                <Select value={editFormData.type} onValueChange={(value: InstrumentType) => setEditFormData({ ...editFormData, type: value })}>
                  <SelectTrigger className="col-span-3">
                    <SelectValue placeholder={t("common.type")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="STOCK">{t("instruments.types.STOCK")}</SelectItem>
                    <SelectItem value="ETF">{t("instruments.types.ETF")}</SelectItem>
                    <SelectItem value="TREASURY_BOND">{t("instruments.types.TREASURY_BOND")}</SelectItem>
                    <SelectItem value="FIXED_INCOME">{t("instruments.types.FIXED_INCOME")}</SelectItem>
                    <SelectItem value="FUND">{t("instruments.types.FUND")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-currency" className="text-right">{t("common.currency")}</Label>
                <Input
                  id="edit-currency"
                  value={editFormData.currency}
                  onChange={(e) => setEditFormData({ ...editFormData, currency: e.target.value.toUpperCase() })}
                  placeholder="BRL"
                  maxLength={3}
                  className="col-span-3 uppercase"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-market" className="text-right">{t("instruments.market")} ({t("common.optional")})</Label>
                <Input
                  id="edit-market"
                  value={editFormData.market}
                  onChange={(e) => setEditFormData({ ...editFormData, market: e.target.value })}
                  placeholder="B3"
                  className="col-span-3"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-isin" className="text-right">ISIN ({t("common.optional")})</Label>
                <Input
                  id="edit-isin"
                  value={editFormData.isin}
                  onChange={(e) => setEditFormData({ ...editFormData, isin: e.target.value.toUpperCase() })}
                  placeholder="BRPETRACNPR6"
                  maxLength={12}
                  className="col-span-3 uppercase"
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="edit-status" className="text-right">{t("common.status")}</Label>
                <Select value={editFormData.status} onValueChange={(value: InstrumentStatus) => setEditFormData({ ...editFormData, status: value })}>
                  <SelectTrigger className="col-span-3">
                    <SelectValue placeholder={t("common.status")} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ACTIVE">{t("instruments.status.ACTIVE")}</SelectItem>
                    <SelectItem value="INACTIVE">{t("instruments.status.INACTIVE")}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsEditDialogOpen(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={isSubmitting || !editFormData.symbol || !editFormData.name}>
                {isSubmitting ? t("instruments.saving") : t("common.save")}
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
          <div className="flex items-center space-x-2">
            <Briefcase className="h-5 w-5 text-muted-foreground" />
            <CardTitle>
              {selectedLedger
                ? `${t("instruments.title")} - ${selectedLedger.attributes.name}`
                : t("instruments.noLedgerSelected")}
            </CardTitle>
          </div>
          <CardDescription>
            {t("instruments.listDescription")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : instruments.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <TrendingUp className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("instruments.noInstruments")}</h3>
              <p className="text-muted-foreground">
                {t("instruments.noInstrumentsDescription")}
              </p>
            </div>
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("instruments.symbol")}</TableHead>
                    <TableHead>{t("common.name")}</TableHead>
                    <TableHead>{t("common.type")}</TableHead>
                    <TableHead>{t("common.currency")}</TableHead>
                    <TableHead>{t("instruments.market")}</TableHead>
                    <TableHead>{t("common.status")}</TableHead>
                    <TableHead className="w-[70px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {instruments.map((instrument) => (
                    <TableRow key={instrument.id}>
                      <TableCell className="font-medium font-mono">
                        {instrument.attributes.symbol}
                      </TableCell>
                      <TableCell>{instrument.attributes.name}</TableCell>
                      <TableCell>
                        <Badge variant={instrumentTypeColors[instrument.attributes.type]}>
                          {t(`instruments.types.${instrument.attributes.type}`)}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{instrument.attributes.currency}</Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {instrument.attributes.market || "-"}
                      </TableCell>
                      <TableCell>
                        <Badge variant={instrument.attributes.status === "ACTIVE" ? "default" : "secondary"}>
                          {t(`instruments.status.${instrument.attributes.status}`)}
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
                            <DropdownMenuItem onClick={() => handleEdit(instrument)}>
                              <Pencil className="mr-2 h-4 w-4" />
                              {t("common.edit")}
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              onClick={() => handleDelete(instrument.id)}
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
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
