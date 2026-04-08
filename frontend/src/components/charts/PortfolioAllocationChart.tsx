import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getPortfolio } from "@/api/instruments"
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
]

export function PortfolioAllocationChart({ ledgers, defaultCurrency }: Props) {
  const { t } = useTranslation()
  const [data, setData] = useState<SliceData[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    Promise.all(ledgers.map((l) => getPortfolio(l.attributes.slug)))
      .then((responses) => {
        const totals: Record<string, number> = {}
        for (const res of responses) {
          for (const pos of res.data) {
            const { instrumentType, currentValue, totalCost } = pos.attributes
            const value = currentValue ?? totalCost
            totals[instrumentType] = (totals[instrumentType] || 0) + value
          }
        }

        const slices = Object.entries(totals)
          .map(([type, value]) => ({
            name: t(`instruments.types.${type}`),
            value,
          }))
          .filter((s) => s.value > 0)
          .sort((a, b) => b.value - a.value)

        setData(slices)
      })
      .catch(() => setData([]))
      .finally(() => setIsLoading(false))
  }, [ledgers, t])

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.charts.portfolioAllocation")}</CardTitle>
        <CardDescription>{t("dashboard.charts.portfolioAllocationDescription")}</CardDescription>
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
                contentStyle={{ borderRadius: "8px", border: "1px solid var(--color-border)", background: "var(--color-card)", color: "var(--color-card-foreground)" }}
              />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  )
}
