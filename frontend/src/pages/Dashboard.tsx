import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"
import { getLedgers } from "@/api/ledgers"
import { getBalanceSummary } from "@/api/accounts"
import { getTransactions, getMonthlyReport } from "@/api/transactions"
import type { MonthlyReport } from "@/api/transactions"
import type { LedgerResource, TransactionResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { Wallet, ArrowUpRight, ArrowDownRight, BookOpen } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { IncomeVsExpensesChart } from "@/components/charts/IncomeVsExpensesChart"
import { ExpenseBreakdownChart } from "@/components/charts/ExpenseBreakdownChart"
import { BudgetUtilizationChart } from "@/components/charts/BudgetUtilizationChart"
import { PortfolioAllocationChart } from "@/components/charts/PortfolioAllocationChart"

export function Dashboard() {
  const { t } = useTranslation()
  const { isAuthenticated, user } = useAuth()
  const handleError = useErrorHandler()
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [totalBalanceByCurrency, setTotalBalanceByCurrency] = useState<Record<string, number>>({})
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
  const [monthlyReport, setMonthlyReport] = useState<Record<string, MonthlyReport>>({})
  const [isLoading, setIsLoading] = useState(true)
  const [defaultCurrency, setDefaultCurrency] = useState("BRL")

  useEffect(() => {
    if (isAuthenticated) {
      getLedgers()
        .then(async (response) => {
          setLedgers(response.data)

          if (response.data.length > 0) {
            // Use first ledger's currency as default
            setDefaultCurrency(response.data[0].attributes.currency)

            const allTransactions: TransactionResource[] = []
            const aggregatedBalances: Record<string, number> = {}

            const now = new Date()
            const currentYear = now.getFullYear()
            const currentMonth = now.getMonth() + 1

            await Promise.all(
              response.data.map(async (ledger) => {
                const [balanceSummary, transactionsRes, report] = await Promise.all([
                  getBalanceSummary(ledger.attributes.slug),
                  getTransactions(ledger.attributes.slug, { "page[size]": 20 }),
                  getMonthlyReport(ledger.attributes.slug, currentYear, currentMonth),
                ])
                Object.entries(balanceSummary.balanceByCurrency).forEach(([currency, balance]) => {
                  aggregatedBalances[currency] = (aggregatedBalances[currency] || 0) + balance
                })
                allTransactions.push(...transactionsRes.data)
                setMonthlyReport(prev => ({ ...prev, [ledger.attributes.slug]: report }))
              })
            )

            setTotalBalanceByCurrency(aggregatedBalances)
            // Sort transactions by date descending
            allTransactions.sort((a, b) =>
              new Date(b.attributes.date).getTime() - new Date(a.attributes.date).getTime()
            )
            setTransactions(allTransactions)
          }
        })
        .catch((e) => handleError(e, "fetchFailed"))
        .finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [isAuthenticated, handleError])

  // Aggregate monthly income and expenses from API reports across all ledgers
  const monthlyIncomeByCurrency: Record<string, number> = {}
  const monthlyExpensesByCurrency: Record<string, number> = {}
  Object.values(monthlyReport).forEach((report) => {
    Object.entries(report.incomeByCurrency).forEach(([currency, amount]) => {
      monthlyIncomeByCurrency[currency] = (monthlyIncomeByCurrency[currency] || 0) + amount
    })
    Object.entries(report.expensesByCurrency).forEach(([currency, amount]) => {
      monthlyExpensesByCurrency[currency] = (monthlyExpensesByCurrency[currency] || 0) + amount
    })
  })

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("dashboard.welcomeTitle")}</CardTitle>
            <CardDescription>
              {t("dashboard.welcomeDescription")}
            </CardDescription>
          </CardHeader>
          <CardContent className="text-center">
            <p className="text-muted-foreground">
              {t("dashboard.welcomeText")}
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">
          {t("dashboard.welcome", { name: user?.name })}
        </h2>
        <p className="text-muted-foreground">
          {t("dashboard.overview")}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.totalLedgers")}</CardTitle>
            <BookOpen className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-2xl font-bold">{ledgers.length}</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.totalBalance")}</CardTitle>
            <Wallet className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="space-y-1">
                {Object.entries(totalBalanceByCurrency).map(([currency, balance]) => (
                  <div key={currency} className="text-2xl font-bold">
                    {formatCurrency(balance, currency)}
                  </div>
                ))}
                {Object.keys(totalBalanceByCurrency).length === 0 && (
                  <div className="text-2xl font-bold">{formatCurrency(0, defaultCurrency)}</div>
                )}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("dashboard.acrossAllAccounts")}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.income")}</CardTitle>
            <ArrowUpRight className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="space-y-1">
                {Object.entries(monthlyIncomeByCurrency).map(([currency, amount]) => (
                  <div key={currency} className="text-2xl font-bold text-green-600">
                    {formatCurrency(amount, currency)}
                  </div>
                ))}
                {Object.keys(monthlyIncomeByCurrency).length === 0 && (
                  <div className="text-2xl font-bold text-green-600">{formatCurrency(0, defaultCurrency)}</div>
                )}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("dashboard.thisMonth")}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.expenses")}</CardTitle>
            <ArrowDownRight className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="space-y-1">
                {Object.entries(monthlyExpensesByCurrency).map(([currency, amount]) => (
                  <div key={currency} className="text-2xl font-bold text-red-600">
                    {formatCurrency(amount, currency)}
                  </div>
                ))}
                {Object.keys(monthlyExpensesByCurrency).length === 0 && (
                  <div className="text-2xl font-bold text-red-600">{formatCurrency(0, defaultCurrency)}</div>
                )}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("dashboard.thisMonth")}</p>
          </CardContent>
        </Card>
      </div>

      {!isLoading && ledgers.length > 0 && (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            <IncomeVsExpensesChart ledgers={ledgers} defaultCurrency={defaultCurrency} />
            <ExpenseBreakdownChart ledgers={ledgers} defaultCurrency={defaultCurrency} />
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <BudgetUtilizationChart ledgers={ledgers} defaultCurrency={defaultCurrency} />
            <PortfolioAllocationChart ledgers={ledgers} defaultCurrency={defaultCurrency} />
          </div>
        </>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("dashboard.recentTransactions")}</CardTitle>
            <CardDescription>{t("dashboard.latestActivities")}</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : transactions.length > 0 ? (
              <div className="space-y-3">
                {transactions.slice(0, 5).map((transaction) => (
                  <div
                    key={transaction.id}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div className="flex-1 min-w-0">
                      <p className="font-medium truncate">{transaction.attributes.description}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatDate(transaction.attributes.date)}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 ml-2">
                      <div className="flex flex-wrap gap-1 justify-end">
                        {transaction.attributes.entries?.slice(0, 2).map((entry, idx) => (
                          <Badge
                            key={idx}
                            variant={entry.type === "DEBIT" ? "secondary" : "outline"}
                            className="text-xs"
                          >
                            {entry.type === "DEBIT" ? "DB" : "CR"}
                          </Badge>
                        ))}
                      </div>
                      <span className="font-mono text-sm font-medium">
                        {formatCurrency(transaction.attributes.amount, transaction.attributes.currency)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">
                {t("dashboard.noRecentTransactions")}
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("dashboard.yourLedgers")}</CardTitle>
            <CardDescription>{t("dashboard.financialBooks")}</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : ledgers.length > 0 ? (
              <div className="space-y-3">
                {ledgers.slice(0, 5).map((ledger) => (
                  <div
                    key={ledger.id}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div>
                      <p className="font-medium">{ledger.attributes.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {ledger.attributes.currency}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">
                {t("dashboard.noLedgersYet")}
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
