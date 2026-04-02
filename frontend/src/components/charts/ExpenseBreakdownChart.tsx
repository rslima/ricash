import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getAccounts } from "@/api/accounts"
import type { LedgerResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"

interface Props {
  ledgers: LedgerResource[]
  defaultCurrency: string
}

interface SliceData {
  name: string
  value: number
}

const COLORS = [
  "var(--color-chart-1)",
  "var(--color-chart-2)",
  "var(--color-chart-3)",
  "var(--color-chart-4)",
  "var(--color-chart-5)",
  "hsl(250 60% 55%)",
  "hsl(190 50% 50%)",
  "hsl(60 70% 50%)",
]

export function ExpenseBreakdownChart({ ledgers, defaultCurrency }: Props) {
  const { t } = useTranslation()
  const [data, setData] = useState<SliceData[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    Promise.all(
      ledgers.map((l) => getAccounts(l.attributes.slug, { "page[size]": 100 }))
    )
      .then((responses) => {
        const totals: Record<string, number> = {}
        for (const res of responses) {
          for (const account of res.data) {
            const { type, currency, balance, name } = account.attributes
            if (type === "EXPENSE" && currency === defaultCurrency && balance > 0) {
              totals[name] = (totals[name] || 0) + balance
            }
          }
        }

        const sorted = Object.entries(totals)
          .map(([name, value]) => ({ name, value }))
          .sort((a, b) => b.value - a.value)

        if (sorted.length <= 8) {
          setData(sorted)
        } else {
          const top = sorted.slice(0, 7)
          const otherValue = sorted.slice(7).reduce((sum, s) => sum + s.value, 0)
          top.push({ name: t("dashboard.charts.other"), value: otherValue })
          setData(top)
        }
      })
      .catch(() => setData([]))
      .finally(() => setIsLoading(false))
  }, [ledgers, defaultCurrency, t])

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.charts.expenseBreakdown")}</CardTitle>
        <CardDescription>{t("dashboard.charts.expenseBreakdownDescription")}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[300px] w-full" />
        ) : data.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t("dashboard.charts.noData")}
          </p>
        ) : (
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={60}
                outerRadius={100}
                dataKey="value"
                paddingAngle={2}
              >
                {data.map((_, index) => (
                  <Cell key={index} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip
                formatter={(value) => formatCurrency(Number(value), defaultCurrency)}
                contentStyle={{ borderRadius: "8px", border: "1px solid hsl(var(--border))", background: "hsl(var(--card))" }}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  )
}
