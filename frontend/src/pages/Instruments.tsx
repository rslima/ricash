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

// Display order of instrument types in the form Select (mirrors the original hand-listed items).
const INSTRUMENT_TYPE_ORDER: readonly InstrumentType[] = [
  "STOCK",
  "ETF",
  "TREASURY_BOND",
  "FIXED_INCOME",
  "FUND",
]

interface InstrumentFormData {
  symbol: string
  name: string
  type: InstrumentType
  currency: string
  market: string
  isin: string
  status: InstrumentStatus
}

const EMPTY_FORM_DATA: InstrumentFormData = {
  symbol: "",
  name: "",
  type: "STOCK",
  currency: "BRL",
  market: "",
  isin: "",
  status: "ACTIVE",
}

interface InstrumentFormProps {
  idPrefix: string
  formData: InstrumentFormData
  setFormData: (data: InstrumentFormData) => void
  onSubmit: (e: React.FormEvent) => void
  onCancel: () => void
  submitLabel: string
  submittingLabel: string
  isSubmitting: boolean
  showStatus?: boolean
}

function InstrumentForm({
  idPrefix,
  formData,
  setFormData,
  onSubmit,
  onCancel,
  submitLabel,
  submittingLabel,
  isSubmitting,
  showStatus = false,
}: InstrumentFormProps) {
  const { t } = useTranslation()

  return (
    <form onSubmit={onSubmit}>
      <div className="grid gap-4 py-4">
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor={`${idPrefix}symbol`} className="text-right">{t("instruments.symbol")}</Label>
          <Input
            id={`${idPrefix}symbol`}
            value={formData.symbol}
            onChange={(e) => setFormData({ ...formData, symbol: e.target.value.toUpperCase() })}
            placeholder="PETR4"
            className="col-span-3 uppercase"
            required
          />
        </div>
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor={`${idPrefix}name`} className="text-right">{t("common.name")}</Label>
          <Input
            id={`${idPrefix}name`}
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            placeholder="Petrobras PN"
            className="col-span-3"
            required
          />
        </div>
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor={`${idPrefix}type`} className="text-right">{t("common.type")}</Label>
          <Select value={formData.type} onValueChange={(value: InstrumentType) => setFormData({ ...formData, type: value })}>
            <SelectTrigger className="col-span-3">
              <SelectValue placeholder={t("common.type")} />
            </SelectTrigger>
            <SelectContent>
              {INSTRUMENT_TYPE_ORDER.map((type) => (
                <SelectItem key={type} value={type}>{t(`instruments.types.${type}`)}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor={`${idPrefix}currency`} className="text-right">{t("common.currency")}</Label>
          <Input
            id={`${idPrefix}currency`}
            value={formData.currency}
            onChange={(e) => setFormData({ ...formData, currency: e.target.value.toUpperCase() })}
            placeholder="BRL"
            maxLength={3}
            className="col-span-3 uppercase"
            required
          />
        </div>
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor={`${idPrefix}market`} className="text-right">{t("instruments.market")} ({t("common.optional")})</Label>
          <Input
            id={`${idPrefix}market`}
            value={formData.market}
            onChange={(e) => setFormData({ ...formData, market: e.target.value })}
            placeholder="B3"
            className="col-span-3"
          />
        </div>
        <div className="grid grid-cols-4 items-center gap-4">
          <Label htmlFor={`${idPrefix}isin`} className="text-right">ISIN ({t("common.optional")})</Label>
          <Input
            id={`${idPrefix}isin`}
            value={formData.isin}
            onChange={(e) => setFormData({ ...formData, isin: e.target.value.toUpperCase() })}
            placeholder="BRPETRACNPR6"
            maxLength={12}
            className="col-span-3 uppercase"
          />
        </div>
        {showStatus && (
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor={`${idPrefix}status`} className="text-right">{t("common.status")}</Label>
            <Select value={formData.status} onValueChange={(value: InstrumentStatus) => setFormData({ ...formData, status: value })}>
              <SelectTrigger className="col-span-3">
                <SelectValue placeholder={t("common.status")} />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACTIVE">{t("instruments.status.ACTIVE")}</SelectItem>
                <SelectItem value="INACTIVE">{t("instruments.status.INACTIVE")}</SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}
      </div>
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" disabled={isSubmitting || !formData.symbol || !formData.name}>
          {isSubmitting ? submittingLabel : submitLabel}
        </Button>
      </DialogFooter>
    </form>
  )
}

export function Instruments() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false)
  const [editingInstrument, setEditingInstrument] = useState<InstrumentResource | null>(null)

  // Form state (create form never renders/sends `status`; it just carries the default).
  const [formData, setFormData] = useState<InstrumentFormData>(EMPTY_FORM_DATA)
  const [editFormData, setEditFormData] = useState<InstrumentFormData>(EMPTY_FORM_DATA)

  const resetForm = () => {
    setFormData(EMPTY_FORM_DATA)
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
          <InstrumentForm
            idPrefix=""
            formData={formData}
            setFormData={setFormData}
            onSubmit={handleCreate}
            onCancel={() => setIsCreateDialogOpen(false)}
            submitLabel={t("common.create")}
            submittingLabel={t("instruments.creating")}
            isSubmitting={createInstrumentMutation.isPending}
          />
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
          <InstrumentForm
            idPrefix="edit-"
            formData={editFormData}
            setFormData={setEditFormData}
            onSubmit={handleUpdate}
            onCancel={() => setIsEditDialogOpen(false)}
            submitLabel={t("common.save")}
            submittingLabel={t("instruments.saving")}
            isSubmitting={updateInstrumentMutation.isPending}
            showStatus
          />
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
                            <Button variant="ghost" size="icon" aria-label={t("common.actions")}>
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
