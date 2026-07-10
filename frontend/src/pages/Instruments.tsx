import { useState } from "react"
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
import { useInstruments, useCreateInstrument, useUpdateInstrument, useDeleteInstrument } from "@/api/instruments.hooks"
import type { InstrumentResource, InstrumentType, InstrumentStatus } from "@/api/types"
import { Plus, Trash2, TrendingUp, MoreHorizontal, Pencil, Briefcase } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { SignInRequired } from "@/components/SignInRequired"
import { EmptyState } from "@/components/EmptyState"
import { TableSkeleton } from "@/components/ui/table-skeleton"
import { LedgerSelector } from "@/components/LedgerSelector"
import { useLedgerSelection } from "@/hooks/use-ledger-selection"
import { combineQueries, useQueryErrorToast } from "@/hooks/use-query-error-toast"

const instrumentTypeColors: Record<InstrumentType, "default" | "secondary" | "destructive" | "outline"> = {
  STOCK: "default",
  ETF: "secondary",
  TREASURY_BOND: "outline",
  FIXED_INCOME: "outline",
  FUND: "secondary",
}

export function Instruments() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
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

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const ledgerSelection = useLedgerSelection()
  const { ledgers, selectedLedgerSlug, setSelectedLedgerSlug, selectedLedger } = ledgerSelection
  const instrumentsQuery = useInstruments(
    selectedLedgerSlug ?? "",
    { "page[size]": 100 },
    isAuthenticated && !!selectedLedgerSlug
  )
  const instruments = instrumentsQuery.data?.data ?? []
  const { isLoading } = combineQueries(ledgerSelection, instrumentsQuery)

  const createInstrumentMutation = useCreateInstrument(selectedLedgerSlug ?? "")
  const updateInstrumentMutation = useUpdateInstrument(selectedLedgerSlug ?? "")
  const deleteInstrumentMutation = useDeleteInstrument(selectedLedgerSlug ?? "")

  // Surface fetch failures as a toast (mutations report their own errors inline).
  useQueryErrorToast([ledgerSelection, instrumentsQuery])

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug) return

    createInstrumentMutation.mutate(
      {
        symbol: formData.symbol.toUpperCase(),
        name: formData.name,
        type: formData.type,
        currency: formData.currency.toUpperCase(),
        market: formData.market || undefined,
        isin: formData.isin || undefined,
      },
      {
        onSuccess: () => {
          setIsCreateDialogOpen(false)
          resetForm()
        },
        onError: (error) => handleError(error, "createFailed"),
      }
    )
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

  const handleUpdate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !editingInstrument) return

    updateInstrumentMutation.mutate(
      {
        instrumentId: editingInstrument.id,
        input: {
          symbol: editFormData.symbol.toUpperCase(),
          name: editFormData.name,
          type: editFormData.type,
          currency: editFormData.currency.toUpperCase(),
          market: editFormData.market || undefined,
          isin: editFormData.isin || undefined,
          status: editFormData.status,
        },
      },
      {
        onSuccess: () => {
          setIsEditDialogOpen(false)
          setEditingInstrument(null)
        },
        onError: (error) => handleError(error, "updateFailed"),
      }
    )
  }

  const handleDelete = (instrumentId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("instruments.confirmDelete"))) return

    deleteInstrumentMutation.mutate(instrumentId, {
      onError: (error) => handleError(error, "deleteFailed"),
    })
  }

  if (!isAuthenticated) {
    return <SignInRequired resourceKey="nav.instruments" />
  }

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
              <Button type="submit" disabled={createInstrumentMutation.isPending || !formData.symbol || !formData.name}>
                {createInstrumentMutation.isPending ? t("instruments.creating") : t("common.create")}
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
              <Button type="submit" disabled={updateInstrumentMutation.isPending || !editFormData.symbol || !editFormData.name}>
                {updateInstrumentMutation.isPending ? t("instruments.saving") : t("common.save")}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <LedgerSelector
        ledgers={ledgers}
        selectedSlug={selectedLedgerSlug}
        onSelect={setSelectedLedgerSlug}
      />

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
            <TableSkeleton />
          ) : instruments.length === 0 ? (
            <EmptyState
              icon={TrendingUp}
              title={t("instruments.noInstruments")}
              description={t("instruments.noInstrumentsDescription")}
            />
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
