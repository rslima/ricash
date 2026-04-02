import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getMonthlyReport } from "@/api/transactions"
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

export function IncomeVsExpensesChart({ ledgers, defaultCurrency }: Props) {
  const { t } = useTranslation()
  const [data, setData] = useState<MonthData[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const months = getLast6Months()

    Promise.all(
      months.map(async ({ year, month, label }) => {
        const reports = await Promise.all(
          ledgers.map((l) => getMonthlyReport(l.attributes.slug, year, month))
        )
        let income = 0
        let expenses = 0
        for (const report of reports) {
          income += report.incomeByCurrency[defaultCurrency] ?? 0
          expenses += report.expensesByCurrency[defaultCurrency] ?? 0
        }
        return { month: label, income, expenses }
      })
    )
      .then(setData)
      .catch(() => setData([]))
      .finally(() => setIsLoading(false))
  }, [ledgers, defaultCurrency])

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.charts.incomeVsExpenses")}</CardTitle>
        <CardDescription>{t("dashboard.charts.incomeVsExpensesDescription")}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[300px] w-full" />
        ) : data.every((d) => d.income === 0 && d.expenses === 0) ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t("dashboard.charts.noData")}
          </p>
        ) : (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={data}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
              <XAxis dataKey="month" className="text-xs" />
              <YAxis className="text-xs" tickFormatter={(v) => formatCurrency(v, defaultCurrency)} width={100} />
              <Tooltip
                formatter={(value) => formatCurrency(Number(value), defaultCurrency)}
                contentStyle={{ borderRadius: "8px", border: "1px solid hsl(var(--border))", background: "hsl(var(--card))" }}
              />
              <Legend />
              <Bar dataKey="income" name={t("dashboard.charts.income")} fill="var(--color-chart-2)" radius={[4, 4, 0, 0]} />
              <Bar dataKey="expenses" name={t("dashboard.charts.expenses")} fill="var(--color-chart-1)" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  )
}
