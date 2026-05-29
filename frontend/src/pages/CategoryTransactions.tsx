import { useEffect, useState, useMemo } from "react"
import { useParams, useSearchParams, Link } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
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
import { getCategoryTransactions } from "@/api/transactions"
import type { CategoryTransactionResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { ArrowLeft, ArrowLeftRight } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"

const MONTH_KEYS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
] as const

export function CategoryTransactions() {
  const { t } = useTranslation()
  const { ledgerSlug, accountId } = useParams<{ ledgerSlug: string; accountId: string }>()
  const [searchParams] = useSearchParams()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()

  const year = parseInt(searchParams.get("year") ?? "")
  const month = parseInt(searchParams.get("month") ?? "")
  const name = searchParams.get("name") ?? ""
  const currency = searchParams.get("currency") ?? "BRL"

  const [transactions, setTransactions] = useState<CategoryTransactionResource[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const monthLabel = useMemo(() => {
    if (!month || month < 1 || month > 12 || !year) return ""
    return `${t(`budget.months.${MONTH_KEYS[month - 1]}`)} ${year}`
  }, [t, month, year])

  const total = useMemo(
    () => transactions.reduce((sum, tx) => sum + tx.attributes.amount, 0),
    [transactions]
  )

  useEffect(() => {
    if (!isAuthenticated || !ledgerSlug || !accountId || !year || !month) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    getCategoryTransactions(ledgerSlug, accountId, year, month)
      .then((response) => setTransactions(response.data))
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [isAuthenticated, ledgerSlug, accountId, year, month, handleError])

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.reports").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <Link to="/reports">
          <Button variant="outline" size="icon">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{name}</h2>
          <p className="text-muted-foreground">{monthLabel}</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("reports.categoryTransactions")}</CardTitle>
          <CardDescription>
            {t("accountTransactions.transactionsFound", { count: transactions.length })}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : transactions.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.date")}</TableHead>
                  <TableHead>{t("common.description")}</TableHead>
                  <TableHead className="text-right">{t("common.amount")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map((transaction) => (
                  <TableRow key={transaction.id}>
                    <TableCell className="font-medium">
                      {formatDate(transaction.attributes.date)}
                    </TableCell>
                    <TableCell>{transaction.attributes.description}</TableCell>
                    <TableCell className="text-right font-mono">
                      {formatCurrency(transaction.attributes.amount, transaction.attributes.currency)}
                    </TableCell>
                  </TableRow>
                ))}
                <TableRow className="border-t-2 font-semibold">
                  <TableCell colSpan={2}>{t("reports.total")}</TableCell>
                  <TableCell className="text-right font-mono">
                    {formatCurrency(total, currency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <p className="text-sm text-muted-foreground">{t("reports.noCategoryTransactions")}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
