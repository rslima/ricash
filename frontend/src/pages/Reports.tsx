import { useEffect, useMemo, useState } from "react"
import { useNavigate } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAuth } from "@/contexts/AuthContext"
import { getLedgers } from "@/api/ledgers"
import { getAccounts } from "@/api/accounts"
import {
  getMonthlyIncomeBreakdown,
  getMonthlyExpenseBreakdown,
} from "@/api/transactions"
import type { LedgerResource, AccountResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { ArrowUpRight, ArrowDownRight, Scale } from "lucide-react"

const MONTH_KEYS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
] as const

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
      value: valueByAccountId[a.id],
      currency: a.attributes.currency,
      ledgerSlug: ledgerSlugByAccountId[a.id],
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
  const handleError = useErrorHandler()

  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [ledgerSlugByAccountId, setLedgerSlugByAccountId] = useState<Record<string, string>>({})
  const [incomeByAccountId, setIncomeByAccountId] = useState<Record<string, number>>({})
  const [expenseByAccountId, setExpenseByAccountId] = useState<Record<string, number>>({})
  const [isLoading, setIsLoading] = useState(true)

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

  // Load ledgers once.
  useEffect(() => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }
    getLedgers()
      .then((response) => setLedgers(response.data))
      .catch((e) => handleError(e, "fetchFailed"))
  }, [isAuthenticated, handleError])

  // Load INCOME/EXPENSE accounts whenever the set of ledgers changes.
  useEffect(() => {
    if (ledgers.length === 0) return
    Promise.all(ledgers.map((l) => getAccounts(l.attributes.slug, { "page[size]": 200 })))
      .then((responses) => {
        const collected: AccountResource[] = []
        const slugByAccountId: Record<string, string> = {}
        responses.forEach((res, index) => {
          const ledgerSlug = ledgers[index].attributes.slug
          for (const account of res.data) {
            if (account.attributes.type === "INCOME" || account.attributes.type === "EXPENSE") {
              collected.push(account)
              slugByAccountId[account.id] = ledgerSlug
            }
          }
        })
        setAccounts(collected)
        setLedgerSlugByAccountId(slugByAccountId)
      })
      .catch((e) => handleError(e, "fetchFailed"))
  }, [ledgers, handleError])

  // Load the income/expense breakdowns for the selected month.
  useEffect(() => {
    if (ledgers.length === 0) {
      setIsLoading(false)
      return
    }
    setIsLoading(true)
    Promise.all([
      Promise.all(ledgers.map((l) => getMonthlyIncomeBreakdown(l.attributes.slug, selectedYear, selectedMonth))),
      Promise.all(ledgers.map((l) => getMonthlyExpenseBreakdown(l.attributes.slug, selectedYear, selectedMonth))),
    ])
      .then(([incomeReports, expenseReports]) => {
        const income: Record<string, number> = {}
        for (const report of incomeReports) {
          for (const [accountId, amount] of Object.entries(report.incomeByAccountId ?? {})) {
            income[accountId] = (income[accountId] ?? 0) + Number(amount)
          }
        }
        const expense: Record<string, number> = {}
        for (const report of expenseReports) {
          for (const [accountId, amount] of Object.entries(report.expensesByAccountId ?? {})) {
            expense[accountId] = (expense[accountId] ?? 0) + Number(amount)
          }
        }
        setIncomeByAccountId(income)
        setExpenseByAccountId(expense)
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [ledgers, selectedYear, selectedMonth, handleError])

  const yearOptions = useMemo(() => {
    const current = now.getFullYear()
    const years = new Set<number>()
    for (let y = current - 5; y <= current + 1; y++) years.add(y)
    years.add(selectedYear)
    return [...years].sort((a, b) => a - b)
  }, [now, selectedYear])

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

  const monthLabel = `${t(`budget.months.${MONTH_KEYS[selectedMonth - 1]}`)} ${selectedYear}`

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
        <div className="flex items-center gap-2">
          <Select value={selectedMonth.toString()} onValueChange={(v) => setSelectedMonth(parseInt(v))}>
            <SelectTrigger className="h-9 w-36">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {MONTH_KEYS.map((m, index) => (
                <SelectItem key={m} value={(index + 1).toString()}>
                  {t(`budget.months.${m}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={selectedYear.toString()} onValueChange={(v) => setSelectedYear(parseInt(v))}>
            <SelectTrigger className="h-9 w-24">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {yearOptions.map((y) => (
                <SelectItem key={y} value={y.toString()}>
                  {y}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
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
