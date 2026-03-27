import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAuth } from "@/contexts/AuthContext"
import { getPortfolio } from "@/api/instruments"
import { getLedgers } from "@/api/ledgers"
import type { InstrumentPositionResource, LedgerResource, InstrumentType } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { TrendingUp, Briefcase, PieChart, DollarSign } from "lucide-react"
import { cn } from "@/lib/utils"
import { useErrorHandler } from "@/hooks/use-error-handler"

const instrumentTypeColors: Record<InstrumentType, "default" | "secondary" | "destructive" | "outline"> = {
  STOCK: "default",
  ETF: "secondary",
  TREASURY_BOND: "outline",
  FIXED_INCOME: "outline",
  FUND: "secondary",
}

export function Portfolio() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [positions, setPositions] = useState<InstrumentPositionResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isLoading, setIsLoading] = useState(true)

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
    getPortfolio(selectedLedgerSlug)
      .then((response) => {
        setPositions(response.data)
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated])

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.portfolio").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)

  // Group totals by currency
  const totalsByCurrency = positions.reduce((acc, p) => {
    const currency = p.attributes.currency
    if (!acc[currency]) {
      acc[currency] = { cost: 0, value: 0, gain: 0, hasPrice: false }
    }
    acc[currency].cost += p.attributes.totalCost
    acc[currency].value += p.attributes.currentValue || 0
    acc[currency].gain += p.attributes.unrealizedGain || 0
    if (p.attributes.currentPrice !== undefined) {
      acc[currency].hasPrice = true
    }
    return acc
  }, {} as Record<string, { cost: number; value: number; gain: number; hasPrice: boolean }>)

  const currencies = Object.keys(totalsByCurrency)
  const hasCurrentPrices = Object.values(totalsByCurrency).some((t) => t.hasPrice)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t("portfolio.title")}</h1>
          <p className="text-muted-foreground">{t("portfolio.description")}</p>
        </div>
      </div>

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

      {/* Summary Cards */}
      {!isLoading && positions.length > 0 && (
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t("portfolio.totalCost")}
              </CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="space-y-1">
                {currencies.map((currency) => (
                  <div key={currency} className="text-2xl font-bold">
                    {formatCurrency(totalsByCurrency[currency].cost, currency)}
                  </div>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">
                {t("portfolio.investedAmount")}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t("portfolio.currentValue")}
              </CardTitle>
              <PieChart className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="space-y-1">
                {currencies.map((currency) => (
                  <div key={currency} className="text-2xl font-bold">
                    {totalsByCurrency[currency].hasPrice
                      ? formatCurrency(totalsByCurrency[currency].value, currency)
                      : "-"}
                  </div>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">
                {hasCurrentPrices ? t("portfolio.marketValue") : t("portfolio.noPrices")}
              </p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">
                {t("portfolio.unrealizedGain")}
              </CardTitle>
              <TrendingUp className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="space-y-1">
                {currencies.map((currency) => {
                  const totals = totalsByCurrency[currency]
                  const gainPercent = totals.cost > 0 ? ((totals.gain / totals.cost) * 100) : 0
                  return (
                    <div key={currency}>
                      <div className={cn(
                        "text-2xl font-bold",
                        totals.gain >= 0 ? "text-green-500" : "text-red-500"
                      )}>
                        {totals.hasPrice ? (
                          <>
                            {totals.gain >= 0 ? "+" : ""}{formatCurrency(totals.gain, currency)}
                          </>
                        ) : "-"}
                      </div>
                      {totals.hasPrice && (
                        <p className="text-xs text-muted-foreground">
                          {gainPercent >= 0 ? "+" : ""}{gainPercent.toFixed(2)}%
                        </p>
                      )}
                    </div>
                  )
                })}
              </div>
              {!hasCurrentPrices && (
                <p className="text-xs text-muted-foreground">
                  {t("portfolio.addPrices")}
                </p>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      <Card>
        <CardHeader>
          <div className="flex items-center space-x-2">
            <Briefcase className="h-5 w-5 text-muted-foreground" />
            <CardTitle>
              {selectedLedger
                ? `${t("portfolio.positions")} - ${selectedLedger.attributes.name}`
                : t("portfolio.noLedgerSelected")}
            </CardTitle>
          </div>
          <CardDescription>
            {t("portfolio.positionsDescription")}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-2">
              {[1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : positions.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-8 text-center">
              <Briefcase className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("portfolio.noPositions")}</h3>
              <p className="text-muted-foreground">
                {t("portfolio.noPositionsDescription")}
              </p>
            </div>
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("portfolio.instrument")}</TableHead>
                    <TableHead>{t("common.type")}</TableHead>
                    <TableHead className="text-right">{t("portfolio.quantity")}</TableHead>
                    <TableHead className="text-right">{t("portfolio.averageCost")}</TableHead>
                    <TableHead className="text-right">{t("portfolio.totalCost")}</TableHead>
                    <TableHead className="text-right">{t("portfolio.currentPrice")}</TableHead>
                    <TableHead className="text-right">{t("portfolio.currentValue")}</TableHead>
                    <TableHead className="text-right">{t("portfolio.gain")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {positions.map((position) => {
                    const hasPrice = position.attributes.currentPrice !== undefined
                    const gain = position.attributes.unrealizedGain || 0
                    const gainPercent = position.attributes.unrealizedGainPercent || 0

                    return (
                      <TableRow key={position.id}>
                        <TableCell>
                          <div className="flex flex-col">
                            <span className="font-mono font-medium">
                              {position.attributes.instrumentSymbol}
                            </span>
                            <span className="text-xs text-muted-foreground">
                              {position.attributes.instrumentName}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={instrumentTypeColors[position.attributes.instrumentType]}>
                            {t(`instruments.types.${position.attributes.instrumentType}`)}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {position.attributes.quantity.toLocaleString(undefined, {
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 8,
                          })}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {formatCurrency(position.attributes.averageCost, position.attributes.currency)}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {formatCurrency(position.attributes.totalCost, position.attributes.currency)}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {hasPrice
                            ? formatCurrency(position.attributes.currentPrice!, position.attributes.currency)
                            : "-"}
                        </TableCell>
                        <TableCell className="text-right font-mono">
                          {hasPrice
                            ? formatCurrency(position.attributes.currentValue!, position.attributes.currency)
                            : "-"}
                        </TableCell>
                        <TableCell className="text-right">
                          {hasPrice ? (
                            <div className={cn(
                              "flex flex-col items-end",
                              gain >= 0 ? "text-green-500" : "text-red-500"
                            )}>
                              <span className="font-mono">
                                {gain >= 0 ? "+" : ""}{formatCurrency(gain, position.attributes.currency)}
                              </span>
                              <span className="text-xs">
                                {gainPercent >= 0 ? "+" : ""}{gainPercent.toFixed(2)}%
                              </span>
                            </div>
                          ) : (
                            "-"
                          )}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
