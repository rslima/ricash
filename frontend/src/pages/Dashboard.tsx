import { useEffect } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"
import { useQueries } from "@tanstack/react-query"
import { useLedgers } from "@/api/ledgers.hooks"
import { getBalanceSummary } from "@/api/accounts"
import { accountKeys } from "@/api/accounts.hooks"
import { getTransactions, getMonthlyReport } from "@/api/transactions"
import { transactionKeys } from "@/api/transactions.hooks"
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

  // Current month/year drive the monthly report query below.
  const now = new Date()
  const currentYear = now.getFullYear()
  const currentMonth = now.getMonth() + 1

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const {
    data: ledgersResponse,
    isLoading: isLoadingLedgers,
    isError: isLedgersError,
    error: ledgersError,
  } = useLedgers(isAuthenticated)
  const ledgers = ledgersResponse?.data ?? []
  // Use first ledger's currency as default
  const defaultCurrency = ledgers[0]?.attributes.currency ?? "BRL"

  // The dashboard aggregates across ALL ledgers, so fan out one query per
  // ledger. Keys/fns match the single-ledger hooks, so a transaction mutation
  // (which invalidates ["transactions"/"accounts", slug]) refreshes these too.
  const balanceQueries = useQueries({
    queries: ledgers.map((ledger) => ({
      queryKey: accountKeys.balanceSummary(ledger.attributes.slug),
      queryFn: () => getBalanceSummary(ledger.attributes.slug),
      enabled: isAuthenticated,
    })),
  })

  const transactionQueries = useQueries({
    queries: ledgers.map((ledger) => ({
      queryKey: transactionKeys.list(ledger.attributes.slug, { "page[size]": 20 }),
      queryFn: () => getTransactions(ledger.attributes.slug, { "page[size]": 20 }),
      enabled: isAuthenticated,
    })),
  })

  const reportQueries = useQueries({
    queries: ledgers.map((ledger) => ({
      queryKey: transactionKeys.monthlyReport(ledger.attributes.slug, currentYear, currentMonth),
      queryFn: () => getMonthlyReport(ledger.attributes.slug, currentYear, currentMonth),
      enabled: isAuthenticated,
    })),
  })

  // Combine the queries' loading flags to reproduce the old single loading flag.
  const isLoading =
    isLoadingLedgers ||
    balanceQueries.some((q) => q.isLoading) ||
    transactionQueries.some((q) => q.isLoading) ||
    reportQueries.some((q) => q.isLoading)

  // Aggregate balances across all ledgers by currency.
  const totalBalanceByCurrency: Record<string, number> = {}
  balanceQueries.forEach((q) => {
    if (!q.data) return
    Object.entries(q.data.balanceByCurrency).forEach(([currency, balance]) => {
      totalBalanceByCurrency[currency] = (totalBalanceByCurrency[currency] || 0) + balance
    })
  })

  // Concatenate every ledger's recent transactions, newest first.
  const transactions = transactionQueries
    .flatMap((q) => q.data?.data ?? [])
    .sort((a, b) => new Date(b.attributes.date).getTime() - new Date(a.attributes.date).getTime())

  // Surface fetch failures as a toast.
  const isError =
    isLedgersError ||
    balanceQueries.some((q) => q.isError) ||
    transactionQueries.some((q) => q.isError) ||
    reportQueries.some((q) => q.isError)
  const error =
    ledgersError ??
    balanceQueries.find((q) => q.error)?.error ??
    transactionQueries.find((q) => q.error)?.error ??
    reportQueries.find((q) => q.error)?.error
  useEffect(() => {
    if (isError) handleError(error, "fetchFailed")
  }, [isError, error, handleError])

  // Aggregate monthly income and expenses from API reports across all ledgers
  const monthlyIncomeByCurrency: Record<string, number> = {}
  const monthlyExpensesByCurrency: Record<string, number> = {}
  reportQueries.forEach((q) => {
    if (!q.data) return
    Object.entries(q.data.incomeByCurrency).forEach(([currency, amount]) => {
      monthlyIncomeByCurrency[currency] = (monthlyIncomeByCurrency[currency] || 0) + amount
    })
    Object.entries(q.data.expensesByCurrency).forEach(([currency, amount]) => {
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
            <ExpenseBreakdownChart ledgers={ledgers} />
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
