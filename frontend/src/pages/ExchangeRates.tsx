import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAuth } from "@/contexts/AuthContext"
import { getExchangeRates, createExchangeRate, deleteExchangeRate } from "@/api/exchangeRates"
import type { ExchangeRateResource } from "@/api/types"
import { formatDate } from "@/lib/utils"
import { TrendingUp, Plus, Trash2 } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"

export function ExchangeRates() {
  const { t } = useTranslation()
  const handleError = useErrorHandler()
  const { isAuthenticated } = useAuth()
  const [exchangeRates, setExchangeRates] = useState<ExchangeRateResource[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Form state
  const [fromCurrency, setFromCurrency] = useState("")
  const [toCurrency, setToCurrency] = useState("")
  const [rate, setRate] = useState("")
  const [effectiveDate, setEffectiveDate] = useState(new Date().toISOString().split("T")[0])

  const resetForm = () => {
    setFromCurrency("")
    setToCurrency("")
    setRate("")
    setEffectiveDate(new Date().toISOString().split("T")[0])
  }

  const isFormValid = () => {
    return (
      fromCurrency.trim().length === 3 &&
      toCurrency.trim().length === 3 &&
      parseFloat(rate) > 0 &&
      effectiveDate
    )
  }

  const handleSubmit = async () => {
    if (!isFormValid()) return

    setIsSubmitting(true)
    try {
      const response = await createExchangeRate({
        fromCurrency: fromCurrency.toUpperCase(),
        toCurrency: toCurrency.toUpperCase(),
        rate: parseFloat(rate),
        effectiveDate
      })
      setExchangeRates([response.data, ...exchangeRates])
      setIsCreateDialogOpen(false)
      resetForm()
    } catch (error) {
      handleError(error, "createFailed")
    } finally {
      setIsSubmitting(false)
    }
  }

  const fetchExchangeRates = useCallback(async () => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    try {
      const response = await getExchangeRates({ "page[size]": 50 })
      setExchangeRates(response.data)
    } catch (error) {
      handleError(error, "fetchFailed")
    } finally {
      setIsLoading(false)
    }
  }, [isAuthenticated, handleError])

  const handleDelete = async (id: string) => {
    if (!confirm(t("exchangeRates.confirmDelete"))) return

    try {
      await deleteExchangeRate(id)
      setExchangeRates(exchangeRates.filter((r) => r.id !== id))
    } catch (error) {
      handleError(error, "deleteFailed")
    }
  }

  useEffect(() => {
    fetchExchangeRates()
  }, [fetchExchangeRates])

  const getSourceBadgeVariant = (source: string): "default" | "secondary" | "outline" => {
    switch (source) {
      case "MANUAL":
        return "default"
      case "EXTERNAL_API":
        return "secondary"
      default:
        return "outline"
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.exchangeRates").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t("exchangeRates.title")}</h1>
          <p className="text-muted-foreground">{t("exchangeRates.description")}</p>
        </div>
        <Button onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          {t("exchangeRates.newRate")}
        </Button>
      </div>

      {/* Create Dialog */}
      <Dialog open={isCreateDialogOpen} onOpenChange={(open) => {
        setIsCreateDialogOpen(open)
        if (!open) resetForm()
      }}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>{t("exchangeRates.createRate")}</DialogTitle>
            <DialogDescription>
              {t("exchangeRates.createRateDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="fromCurrency" className="text-right">
                {t("exchangeRates.from")}
              </Label>
              <Input
                id="fromCurrency"
                value={fromCurrency}
                onChange={(e) => setFromCurrency(e.target.value.toUpperCase())}
                placeholder="USD"
                maxLength={3}
                className="col-span-3 uppercase"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="toCurrency" className="text-right">
                {t("exchangeRates.to")}
              </Label>
              <Input
                id="toCurrency"
                value={toCurrency}
                onChange={(e) => setToCurrency(e.target.value.toUpperCase())}
                placeholder="BRL"
                maxLength={3}
                className="col-span-3 uppercase"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="rate" className="text-right">
                {t("exchangeRates.rate")}
              </Label>
              <Input
                id="rate"
                type="number"
                step="0.000001"
                min="0"
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                placeholder="5.50"
                className="col-span-3"
              />
            </div>
            <div className="grid grid-cols-4 items-center gap-4">
              <Label htmlFor="effectiveDate" className="text-right">
                {t("exchangeRates.effectiveDate")}
              </Label>
              <Input
                id="effectiveDate"
                type="date"
                value={effectiveDate}
                onChange={(e) => setEffectiveDate(e.target.value)}
                className="col-span-3"
              />
            </div>
            {fromCurrency && toCurrency && rate && (
              <p className="text-sm text-muted-foreground text-center">
                1 {fromCurrency} = {rate} {toCurrency}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button
              type="submit"
              onClick={handleSubmit}
              disabled={!isFormValid() || isSubmitting}
            >
              {isSubmitting ? t("exchangeRates.creating") : t("common.create")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <TrendingUp className="h-5 w-5 text-muted-foreground" />
              <CardTitle>{t("exchangeRates.rates")}</CardTitle>
            </div>
          </div>
          <CardDescription>
            {t("exchangeRates.ratesDescription")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : exchangeRates.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <TrendingUp className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("exchangeRates.noRates")}</h3>
              <p className="text-muted-foreground">
                {t("exchangeRates.noRatesDescription")}
              </p>
            </div>
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("exchangeRates.from")}</TableHead>
                    <TableHead>{t("exchangeRates.to")}</TableHead>
                    <TableHead className="text-right">{t("exchangeRates.rate")}</TableHead>
                    <TableHead>{t("exchangeRates.effectiveDate")}</TableHead>
                    <TableHead>{t("exchangeRates.source")}</TableHead>
                    <TableHead>{t("exchangeRates.createdAt")}</TableHead>
                    <TableHead className="w-[70px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {exchangeRates.map((rate) => (
                    <TableRow key={rate.id}>
                      <TableCell className="font-medium">
                        <Badge variant="outline">{rate.attributes.fromCurrency}</Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline">{rate.attributes.toCurrency}</Badge>
                      </TableCell>
                      <TableCell className="text-right font-mono">
                        {rate.attributes.rate.toFixed(6)}
                      </TableCell>
                      <TableCell>{formatDate(rate.attributes.effectiveDate)}</TableCell>
                      <TableCell>
                        <Badge variant={getSourceBadgeVariant(rate.attributes.source)}>
                          {rate.attributes.source}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatDate(rate.attributes.createdAt)}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDelete(rate.id)}
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
