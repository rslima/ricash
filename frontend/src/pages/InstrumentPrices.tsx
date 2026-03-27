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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useAuth } from "@/contexts/AuthContext"
import { getInstrumentPrices, createInstrumentPrice, deleteInstrumentPrice, getAllInstruments } from "@/api/instruments"
import { getLedgers } from "@/api/ledgers"
import type { InstrumentPriceResource, InstrumentResource, LedgerResource } from "@/api/types"
import { formatDate, formatCurrency } from "@/lib/utils"
import { Plus, Trash2, TrendingUp, DollarSign } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"

export function InstrumentPrices() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [prices, setPrices] = useState<InstrumentPriceResource[]>([])
  const [instruments, setInstruments] = useState<InstrumentResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Form state
  const [instrumentId, setInstrumentId] = useState("")
  const [price, setPrice] = useState("")
  const [effectiveDate, setEffectiveDate] = useState(new Date().toISOString().split("T")[0])

  const resetForm = () => {
    setInstrumentId("")
    setPrice("")
    setEffectiveDate(new Date().toISOString().split("T")[0])
  }

  useEffect(() => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    getLedgers()
      .then((response) => {
        setLedgers(response.data)
        if (!selectedLedgerSlug && response.data.length > 0) {
          setSelectedLedgerSlug(response.data[0].attributes.slug)
        }
      })
      .catch((e) => handleError(e, "fetchFailed"))
  }, [isAuthenticated])

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      getInstrumentPrices(selectedLedgerSlug, { "page[size]": 100 }),
      getAllInstruments(selectedLedgerSlug),
    ])
      .then(([pricesResponse, instrumentsResponse]) => {
        setPrices(pricesResponse.data)
        setInstruments(instrumentsResponse)
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug || !instrumentId || !price) return

    setIsSubmitting(true)
    try {
      const response = await createInstrumentPrice(selectedLedgerSlug, {
        instrumentId,
        price: parseFloat(price),
        effectiveDate,
      })
      // Add symbol to the response for display
      const instrument = instruments.find((i) => i.id === instrumentId)
      if (instrument) {
        response.data.attributes.instrumentSymbol = instrument.attributes.symbol
      }
      setPrices([response.data, ...prices])
      setIsCreateDialogOpen(false)
      resetForm()
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleDelete = async (priceId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm(t("instrumentPrices.confirmDelete"))) return

    try {
      await deleteInstrumentPrice(selectedLedgerSlug, priceId)
      setPrices(prices.filter((p) => p.id !== priceId))
    } catch (error) {
      handleError(error, "deleteFailed")
    }
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
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.instrumentPrices").toLowerCase() })}
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
              <Button type="submit" disabled={isSubmitting || !instrumentId || !price}>
                {isSubmitting ? t("instrumentPrices.creating") : t("common.create")}
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
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : prices.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <TrendingUp className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("instrumentPrices.noPrices")}</h3>
              <p className="text-muted-foreground">
                {t("instrumentPrices.noPricesDescription")}
              </p>
            </div>
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
