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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useAuth } from "@/contexts/AuthContext"
import {
  useInstrumentPrices,
  useAllInstruments,
  useCreateInstrumentPrice,
  useDeleteInstrumentPrice,
} from "@/api/instruments.hooks"
import type { InstrumentPriceResource } from "@/api/types"
import { formatDate, formatCurrency } from "@/lib/utils"
import { todayISO } from "@/lib/dates"
import { Plus, Trash2, TrendingUp, DollarSign } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { useLedgerSelection } from "@/hooks/use-ledger-selection"
import { combineQueries, useQueryErrorToast } from "@/hooks/use-query-error-toast"
import { SignInRequired } from "@/components/SignInRequired"
import { EmptyState } from "@/components/EmptyState"
import { TableSkeleton } from "@/components/ui/table-skeleton"
import { LedgerSelector } from "@/components/LedgerSelector"

export function InstrumentPrices() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)

  // Form state
  const [instrumentId, setInstrumentId] = useState("")
  const [price, setPrice] = useState("")
  const [effectiveDate, setEffectiveDate] = useState(todayISO())

  const resetForm = () => {
    setInstrumentId("")
    setPrice("")
    setEffectiveDate(todayISO())
  }

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const ledgerSelection = useLedgerSelection()
  const { ledgers, selectedLedgerSlug, setSelectedLedgerSlug, selectedLedger } = ledgerSelection

  const pricesQuery = useInstrumentPrices(
    selectedLedgerSlug ?? "",
    { "page[size]": 100 },
    isAuthenticated && !!selectedLedgerSlug
  )
  const prices = pricesQuery.data?.data ?? []

  const instrumentsQuery = useAllInstruments(
    selectedLedgerSlug ?? "",
    isAuthenticated && !!selectedLedgerSlug
  )
  const instruments = instrumentsQuery.data ?? []

  const createPriceMutation = useCreateInstrumentPrice(selectedLedgerSlug ?? "")
  const deletePriceMutation = useDeleteInstrumentPrice(selectedLedgerSlug ?? "")

  const { isLoading } = combineQueries(ledgerSelection, pricesQuery, instrumentsQuery)

  // Surface fetch failures as a toast (mutations report their own errors inline).
  useQueryErrorToast([ledgerSelection, pricesQuery, instrumentsQuery])

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !instrumentId || !price) return

    createPriceMutation.mutate(
      {
        instrumentId,
        price: parseFloat(price),
        effectiveDate,
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

  const handleDelete = (priceId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("instrumentPrices.confirmDelete"))) return

    deletePriceMutation.mutate(priceId, {
      onError: (error) => handleError(error, "deleteFailed"),
    })
  }

  // Create a map for quick instrument lookup
  const instrumentMap = new Map(instruments.map((i) => [i.id, i]))

  const getInstrumentSymbol = (priceEntry: InstrumentPriceResource) => {
    if (priceEntry.attributes.instrumentSymbol) {
      return priceEntry.attributes.instrumentSymbol
    }
    const instrument = instrumentMap.get(priceEntry.attributes.instrumentId)
    return instrument?.attributes.symbol || priceEntry.attributes.instrumentId
  }

  const getInstrumentCurrency = (priceEntry: InstrumentPriceResource) => {
    const instrument = instrumentMap.get(priceEntry.attributes.instrumentId)
    return instrument?.attributes.currency || "BRL"
  }

  if (!isAuthenticated) {
    return <SignInRequired resourceKey="nav.instrumentPrices" />
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t("instrumentPrices.title")}</h1>
          <p className="text-muted-foreground">{t("instrumentPrices.description")}</p>
        </div>
        <Button
          disabled={!selectedLedgerSlug || instruments.length === 0}
          onClick={() => setIsCreateDialogOpen(true)}
        >
          <Plus className="mr-2 h-4 w-4" />
          {t("instrumentPrices.newPrice")}
        </Button>
      </div>

      {/* Create Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={(open) => {
        setIsCreateDialogOpen(open)
        if (!open) resetForm()
      }}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>{t("instrumentPrices.createPrice")}</DialogTitle>
            <DialogDescription>
              {t("instrumentPrices.createDescription")}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            <div className="grid gap-4 py-4">
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="instrument" className="text-right">
                  {t("instrumentPrices.instrument")}
                </Label>
                <Select value={instrumentId} onValueChange={setInstrumentId}>
                  <SelectTrigger className="col-span-3">
                    <SelectValue placeholder={t("instrumentPrices.selectInstrument")} />
                  </SelectTrigger>
                  <SelectContent>
                    {instruments.map((instrument) => (
                      <SelectItem key={instrument.id} value={instrument.id}>
                        {instrument.attributes.symbol} - {instrument.attributes.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="price" className="text-right">
                  {t("instrumentPrices.price")}
                </Label>
                <Input
                  id="price"
                  type="number"
                  step="0.000001"
                  min="0"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  placeholder="35.50"
                  className="col-span-3"
                  required
                />
              </div>
              <div className="grid grid-cols-4 items-center gap-4">
                <Label htmlFor="effectiveDate" className="text-right">
                  {t("instrumentPrices.effectiveDate")}
                </Label>
                <Input
                  id="effectiveDate"
                  type="date"
                  value={effectiveDate}
                  onChange={(e) => setEffectiveDate(e.target.value)}
                  className="col-span-3"
                  required
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsCreateDialogOpen(false)}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={createPriceMutation.isPending || !instrumentId || !price}>
                {createPriceMutation.isPending ? t("instrumentPrices.creating") : t("common.create")}
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
            <DollarSign className="h-5 w-5 text-muted-foreground" />
            <CardTitle>
              {selectedLedger
                ? `${t("instrumentPrices.title")} - ${selectedLedger.attributes.name}`
                : t("instrumentPrices.noLedgerSelected")}
            </CardTitle>
          </div>
          <CardDescription>
            {t("instrumentPrices.listDescription")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <TableSkeleton />
          ) : prices.length === 0 ? (
            <EmptyState
              icon={TrendingUp}
              title={t("instrumentPrices.noPrices")}
              description={t("instrumentPrices.noPricesDescription")}
            />
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("instrumentPrices.instrument")}</TableHead>
                    <TableHead className="text-right">{t("instrumentPrices.price")}</TableHead>
                    <TableHead>{t("instrumentPrices.effectiveDate")}</TableHead>
                    <TableHead>{t("instrumentPrices.source")}</TableHead>
                    <TableHead>{t("instrumentPrices.createdAt")}</TableHead>
                    <TableHead className="w-[70px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {prices.map((priceEntry) => (
                    <TableRow key={priceEntry.id}>
                      <TableCell className="font-medium font-mono">
                        <Badge variant="outline">{getInstrumentSymbol(priceEntry)}</Badge>
                      </TableCell>
                      <TableCell className="text-right font-mono">
                        {formatCurrency(priceEntry.attributes.price, getInstrumentCurrency(priceEntry))}
                      </TableCell>
                      <TableCell>{formatDate(priceEntry.attributes.effectiveDate)}</TableCell>
                      <TableCell>
                        <Badge variant={priceEntry.attributes.source === "MANUAL" ? "default" : "secondary"}>
                          {priceEntry.attributes.source}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatDate(priceEntry.attributes.createdAt)}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDelete(priceEntry.id)}
                          className="text-destructive hover:text-destructive"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
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
