import { useMemo, useState } from "react"
import { useQueries } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { MonthYearPicker } from "@/components/MonthYearPicker"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAuth } from "@/contexts/AuthContext"
import { getAccounts } from "@/api/accounts"
import {
  getMonthlyIncomeBreakdown,
  getMonthlyExpenseBreakdown,
} from "@/api/transactions"
import { useLedgers } from "@/api/ledgers.hooks"
import { accountKeys } from "@/api/accounts.hooks"
import { transactionKeys } from "@/api/transactions.hooks"
import type { AccountResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { getMonthLabel } from "@/lib/dates"
import { useQueryErrorToast } from "@/hooks/use-query-error-toast"
import { ArrowUpRight, ArrowDownRight, Scale } from "lucide-react"

interface CategoryRow {
  accountId: string
  name: string
  value: number
  currency: string
  ledgerSlug: string
}

// Builds the top-level category rows for a currency. Breakdown values for a
// parent account already include its descendants, so we only display
// top-level (parentAccountId === null) accounts to avoid double-counting.
function buildCategoryRows(
  accounts: AccountResource[],
  valueByAccountId: Record<string, number>,
  ledgerSlugByAccountId: Record<string, string>,
  type: "INCOME" | "EXPENSE",
  currency: string
): CategoryRow[] {
  return accounts
    .filter(
      (a) =>
        a.attributes.type === type &&
        a.attributes.currency === currency &&
        a.attributes.parentAccountId === null &&
        (valueByAccountId[a.id] ?? 0) > 0
    )
    .map((a) => ({
      accountId: a.id,
      name: a.attributes.name,
      value: valueByAccountId[a.id] ?? 0,
      currency: a.attributes.currency,
      ledgerSlug: ledgerSlugByAccountId[a.id] ?? "",
    }))
    .sort((a, b) => b.value - a.value)
}

function sumRows(rows: CategoryRow[]): number {
  return rows.reduce((sum, r) => sum + r.value, 0)
}

interface BreakdownTableProps {
  rows: CategoryRow[]
  total: number
  currency: string
  barColor: string
  emptyLabel: string
  onRowClick: (row: CategoryRow) => void
}

function BreakdownTable({ rows, total, currency, barColor, emptyLabel, onRowClick }: BreakdownTableProps) {
  const { t } = useTranslation()
  if (rows.length === 0) {
    return <p className="text-sm text-muted-foreground text-center py-8">{emptyLabel}</p>
  }
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t("reports.category")}</TableHead>
          <TableHead className="text-right">{t("reports.amount")}</TableHead>
          <TableHead className="w-[120px] text-right">{t("reports.share")}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row) => {
          const percent = total > 0 ? (row.value / total) * 100 : 0
          return (
            <TableRow
              key={row.accountId}
              className="cursor-pointer"
              onClick={() => onRowClick(row)}
            >
              <TableCell className="font-medium">{row.name}</TableCell>
              <TableCell className="text-right font-mono">
                {formatCurrency(row.value, currency)}
              </TableCell>
              <TableCell>
                <div className="flex items-center justify-end gap-2">
                  <div className="h-2 w-12 overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full"
                      style={{ width: `${percent}%`, backgroundColor: barColor }}
                    />
                  </div>
                  <span className="w-10 text-right text-xs text-muted-foreground">
                    {percent.toFixed(0)}%
                  </span>
                </div>
              </TableCell>
            </TableRow>
          )
        })}
        <TableRow className="border-t-2 font-semibold">
          <TableCell>{t("reports.total")}</TableCell>
          <TableCell className="text-right font-mono">{formatCurrency(total, currency)}</TableCell>
          <TableCell />
        </TableRow>
      </TableBody>
    </Table>
  )
}

export function Reports() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  const now = useMemo(() => new Date(), [])
  const [selectedYear, setSelectedYear] = useState(now.getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1)

  const handleCategoryClick = (row: CategoryRow) => {
    navigate(
      `/ledgers/${row.ledgerSlug}/reports/categories/${row.accountId}` +
        `?year=${selectedYear}&month=${selectedMonth}` +
        `&name=${encodeURIComponent(row.name)}&currency=${row.currency}`
    )
  }

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  // Reports aggregate across every ledger, so the per-ledger account and
  // breakdown fetches fan out with `useQueries` (the single-ledger hooks can't
  // be called in a loop). Query keys mirror the account/transaction hooks so
  // cache entries and invalidations stay in sync.
  const {
    data: ledgersResponse,
    isLoading: ledgersLoading,
    isError: ledgersIsError,
    error: ledgersError,
  } = useLedgers(isAuthenticated)
  const ledgers = useMemo(() => ledgersResponse?.data ?? [], [ledgersResponse])

  const accountQueries = useQueries({
    queries: ledgers.map((l) => ({
      queryKey: accountKeys.list(l.attributes.slug, { "page[size]": 200 }),
      queryFn: () => getAccounts(l.attributes.slug, { "page[size]": 200 }),
      enabled: isAuthenticated,
    })),
  })

  const incomeQueries = useQueries({
    queries: ledgers.map((l) => ({
      queryKey: transactionKeys.incomeBreakdown(l.attributes.slug, selectedYear, selectedMonth),
      queryFn: () => getMonthlyIncomeBreakdown(l.attributes.slug, selectedYear, selectedMonth),
      enabled: isAuthenticated,
    })),
  })

  const expenseQueries = useQueries({
    queries: ledgers.map((l) => ({
      queryKey: transactionKeys.expenseBreakdown(l.attributes.slug, selectedYear, selectedMonth),
      queryFn: () => getMonthlyExpenseBreakdown(l.attributes.slug, selectedYear, selectedMonth),
      enabled: isAuthenticated,
    })),
  })

  const isLoading =
    ledgersLoading ||
    accountQueries.some((q) => q.isLoading) ||
    incomeQueries.some((q) => q.isLoading) ||
    expenseQueries.some((q) => q.isLoading)

  // Collect the INCOME/EXPENSE accounts across all ledgers, tracking which
  // ledger each account belongs to (for category deep links).
  const { accounts, ledgerSlugByAccountId } = useMemo(() => {
    const collected: AccountResource[] = []
    const slugByAccountId: Record<string, string> = {}
    accountQueries.forEach((query, index) => {
      const ledger = ledgers[index]
      if (!ledger || !query.data) return
      const ledgerSlug = ledger.attributes.slug
      for (const account of query.data.data) {
        if (account.attributes.type === "INCOME" || account.attributes.type === "EXPENSE") {
          collected.push(account)
          slugByAccountId[account.id] = ledgerSlug
        }
      }
    })
    return { accounts: collected, ledgerSlugByAccountId: slugByAccountId }
  }, [accountQueries, ledgers])

  // Sum the income breakdowns across every ledger for the selected month.
  const incomeByAccountId = useMemo(() => {
    const income: Record<string, number> = {}
    for (const query of incomeQueries) {
      if (!query.data) continue
      for (const [accountId, amount] of Object.entries(query.data.incomeByAccountId ?? {})) {
        income[accountId] = (income[accountId] ?? 0) + Number(amount)
      }
    }
    return income
  }, [incomeQueries])

  // Sum the expense breakdowns across every ledger for the selected month.
  const expenseByAccountId = useMemo(() => {
    const expense: Record<string, number> = {}
    for (const query of expenseQueries) {
      if (!query.data) continue
      for (const [accountId, amount] of Object.entries(query.data.expensesByAccountId ?? {})) {
        expense[accountId] = (expense[accountId] ?? 0) + Number(amount)
      }
    }
    return expense
  }, [expenseQueries])

  // Surface any fetch failure as a toast.
  useQueryErrorToast([
    { isLoading: ledgersLoading, isError: ledgersIsError, error: ledgersError },
    ...accountQueries,
    ...incomeQueries,
    ...expenseQueries,
  ])

  // All currencies that have any income or expense activity this month.
  const currencies = useMemo(() => {
    const set = new Set<string>()
    for (const account of accounts) {
      if (account.attributes.parentAccountId !== null) continue
      const hasIncome = (incomeByAccountId[account.id] ?? 0) > 0
      const hasExpense = (expenseByAccountId[account.id] ?? 0) > 0
      if (hasIncome || hasExpense) set.add(account.attributes.currency)
    }
    return [...set].sort()
  }, [accounts, incomeByAccountId, expenseByAccountId])

  // Per-currency totals for the summary cards.
  const totalsByCurrency = useMemo(() => {
    return currencies.map((currency) => {
      const income = sumRows(buildCategoryRows(accounts, incomeByAccountId, ledgerSlugByAccountId, "INCOME", currency))
      const expenses = sumRows(buildCategoryRows(accounts, expenseByAccountId, ledgerSlugByAccountId, "EXPENSE", currency))
      return { currency, income, expenses, net: income - expenses }
    })
  }, [currencies, accounts, incomeByAccountId, expenseByAccountId, ledgerSlugByAccountId])

  const monthLabel = getMonthLabel(t, selectedYear, selectedMonth)

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("reports.title")}</CardTitle>
            <CardDescription>{t("dashboard.welcomeDescription")}</CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t("reports.title")}</h2>
          <p className="text-muted-foreground">{t("reports.subtitle")}</p>
        </div>
        <MonthYearPicker
          month={selectedMonth}
          year={selectedYear}
          onMonthChange={setSelectedMonth}
          onYearChange={setSelectedYear}
        />
      </div>

      {/* Summary cards */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("reports.totalIncome")}</CardTitle>
            <ArrowUpRight className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : totalsByCurrency.length === 0 ? (
              <div className="text-2xl font-bold text-green-600">—</div>
            ) : (
              <div className="space-y-1">
                {totalsByCurrency.map((row) => (
                  <div key={row.currency} className="text-2xl font-bold text-green-600">
                    {formatCurrency(row.income, row.currency)}
                  </div>
                ))}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{monthLabel}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("reports.totalExpenses")}</CardTitle>
            <ArrowDownRight className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : totalsByCurrency.length === 0 ? (
              <div className="text-2xl font-bold text-red-600">—</div>
            ) : (
              <div className="space-y-1">
                {totalsByCurrency.map((row) => (
                  <div key={row.currency} className="text-2xl font-bold text-red-600">
                    {formatCurrency(row.expenses, row.currency)}
                  </div>
                ))}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{monthLabel}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("reports.netResult")}</CardTitle>
            <Scale className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : totalsByCurrency.length === 0 ? (
              <div className="text-2xl font-bold">—</div>
            ) : (
              <div className="space-y-1">
                {totalsByCurrency.map((row) => (
                  <div
                    key={row.currency}
                    className={`text-2xl font-bold ${row.net >= 0 ? "text-green-600" : "text-red-600"}`}
                  >
                    {formatCurrency(row.net, row.currency)}
                  </div>
                ))}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("reports.incomeMinusExpenses")}</p>
          </CardContent>
        </Card>
      </div>

      {/* Detailed breakdowns per currency */}
      {isLoading ? (
        <div className="grid gap-4 md:grid-cols-2">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-64 w-full" />
        </div>
      ) : currencies.length === 0 ? (
        <Card>
          <CardContent className="py-12">
            <p className="text-sm text-muted-foreground text-center">{t("reports.noData")}</p>
          </CardContent>
        </Card>
      ) : (
        currencies.map((currency) => {
          const incomeRows = buildCategoryRows(accounts, incomeByAccountId, ledgerSlugByAccountId, "INCOME", currency)
          const expenseRows = buildCategoryRows(accounts, expenseByAccountId, ledgerSlugByAccountId, "EXPENSE", currency)
          return (
            <div key={currency} className="space-y-3">
              {currencies.length > 1 && (
                <h3 className="text-sm font-semibold text-muted-foreground">{currency}</h3>
              )}
              <div className="grid gap-4 md:grid-cols-2">
                <Card>
                  <CardHeader>
                    <CardTitle className="text-base">{t("reports.incomeByCategory")}</CardTitle>
                    <CardDescription>{monthLabel}</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <BreakdownTable
                      rows={incomeRows}
                      total={sumRows(incomeRows)}
                      currency={currency}
                      barColor="var(--color-chart-2)"
                      emptyLabel={t("reports.noIncome")}
                      onRowClick={handleCategoryClick}
                    />
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader>
                    <CardTitle className="text-base">{t("reports.expensesByCategory")}</CardTitle>
                    <CardDescription>{monthLabel}</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <BreakdownTable
                      rows={expenseRows}
                      total={sumRows(expenseRows)}
                      currency={currency}
                      barColor="var(--color-chart-1)"
                      emptyLabel={t("reports.noExpenses")}
                      onRowClick={handleCategoryClick}
                    />
                  </CardContent>
                </Card>
              </div>
            </div>
          )
        })
      )}
    </div>
  )
}
