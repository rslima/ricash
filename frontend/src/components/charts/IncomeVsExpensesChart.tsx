import { useMemo } from "react"
import { useTranslation } from "react-i18next"
import { useQueries } from "@tanstack/react-query"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { ChartCard } from "@/components/charts/ChartCard"
import { CHART_TOOLTIP_CONTENT_STYLE } from "@/components/charts/chart-theme"
import { getMonthlyReport } from "@/api/transactions"
import { transactionKeys } from "@/api/transactions.hooks"
import type { MonthlyReport } from "@/api/transactions"
import type { LedgerResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"

interface Props {
  ledgers: LedgerResource[]
  defaultCurrency: string
}

interface MonthData {
  month: string
  income: number
  expenses: number
}

function getLast6Months(): { year: number; month: number; label: string }[] {
  const months: { year: number; month: number; label: string }[] = []
  const now = new Date()
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    months.push({
      year: d.getFullYear(),
      month: d.getMonth() + 1,
      label: d.toLocaleDateString(undefined, { month: "short" }),
    })
  }
  return months
}

interface MonthReports {
  label: string
  reports: MonthlyReport[]
}

function collectCurrencies(monthReports: MonthReports[]): string[] {
  const currencies = new Set<string>()
  for (const { reports } of monthReports) {
    for (const report of reports) {
      for (const currency of Object.keys(report.incomeByCurrency)) {
        currencies.add(currency)
      }
      for (const currency of Object.keys(report.expensesByCurrency)) {
        currencies.add(currency)
      }
    }
  }
  return Array.from(currencies).sort()
}

function buildChartData(monthReports: MonthReports[], currency: string): MonthData[] {
  return monthReports.map(({ label, reports }) => {
    let income = 0
    let expenses = 0
    for (const report of reports) {
      income += report.incomeByCurrency[currency] ?? 0
      expenses += report.expensesByCurrency[currency] ?? 0
    }
    return { month: label, income, expenses }
  })
}

export function IncomeVsExpensesChart({ ledgers, defaultCurrency }: Props) {
  const { t } = useTranslation()

  const months = useMemo(() => getLast6Months(), [])

  // One flat fan-out over the month x ledger cross product; query i belongs to
  // months[Math.floor(i / ledgers.length)]. Keys mirror transactionKeys so the
  // cache is shared with the rest of the app.
  const reportQueries = useQueries({
    queries: months.flatMap(({ year, month }) =>
      ledgers.map((l) => ({
        queryKey: transactionKeys.monthlyReport(l.attributes.slug, year, month),
        queryFn: () => getMonthlyReport(l.attributes.slug, year, month),
      }))
    ),
  })

  const isLoading = reportQueries.some((q) => q.isLoading)

  const monthReports = useMemo<MonthReports[]>(
    () =>
      months.map(({ label }, monthIndex) => {
        const reports: MonthlyReport[] = []
        for (let i = 0; i < ledgers.length; i++) {
          const data = reportQueries[monthIndex * ledgers.length + i]?.data
          if (data) reports.push(data)
        }
        return { label, reports }
      }),
    [months, ledgers, reportQueries]
  )

  const currencies = collectCurrencies(monthReports)
  // Put defaultCurrency first if present
  const sortedCurrencies = currencies.includes(defaultCurrency)
    ? [defaultCurrency, ...currencies.filter((c) => c !== defaultCurrency)]
    : currencies

  return (
    <ChartCard
      title={t("dashboard.charts.incomeVsExpenses")}
      description={t("dashboard.charts.incomeVsExpensesDescription")}
      isLoading={isLoading}
      isEmpty={sortedCurrencies.length === 0}
      emptyLabel={t("dashboard.charts.noData")}
    >
      <div className="space-y-6">
        {sortedCurrencies.map((currency) => {
          const data = buildChartData(monthReports, currency)
          const hasData = data.some((d) => d.income !== 0 || d.expenses !== 0)
          if (!hasData) return null
          return (
            <div key={currency}>
              {sortedCurrencies.length > 1 && (
                <p className="text-sm font-medium text-muted-foreground mb-2">{currency}</p>
              )}
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={data}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
                  <XAxis dataKey="month" className="text-xs" />
                  <YAxis className="text-xs" tickFormatter={(v) => formatCurrency(v, currency)} width={100} />
                  <Tooltip
                    formatter={(value) => formatCurrency(Number(value), currency)}
                    contentStyle={CHART_TOOLTIP_CONTENT_STYLE}
                  />
                  <Legend />
                  <Bar dataKey="income" name={t("dashboard.charts.income")} fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} />
                  <Bar dataKey="expenses" name={t("dashboard.charts.expenses")} fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )
        })}
      </div>
    </ChartCard>
  )
}
