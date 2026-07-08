import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getBudgetSummary } from "@/api/envelopes"
import { getEnvelopes } from "@/api/envelopes"
import type { LedgerResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"

interface Props {
  ledgers: LedgerResource[]
  defaultCurrency: string
}

interface EnvelopeData {
  name: string
  allocated: number
  spent: number
}

export function BudgetUtilizationChart({ ledgers, defaultCurrency }: Props) {
  const { t } = useTranslation()
  const [data, setData] = useState<EnvelopeData[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1

    Promise.all(
      ledgers.map(async (l) => {
        const slug = l.attributes.slug
        const [budget, envelopesRes] = await Promise.all([
          getBudgetSummary(slug, year, month),
          getEnvelopes(slug, { "page[size]": 100 }),
        ])

        const nameMap = new Map<string, { name: string; type: string; currency: string }>()
        for (const env of envelopesRes.data) {
          nameMap.set(env.id, {
            name: env.attributes.name,
            type: env.attributes.type,
            currency: env.attributes.currency,
          })
        }

        return budget.envelopeBalances
          .filter((b) => {
            const info = nameMap.get(b.envelopeId)
            return info && info.type === "EXPENSE" && info.currency === defaultCurrency
          })
          .map((b) => ({
            name: nameMap.get(b.envelopeId)!.name,
            allocated: b.allocated,
            spent: b.spent,
          }))
      })
    )
      .then((results) => {
        const merged: Record<string, EnvelopeData> = {}
        for (const items of results) {
          for (const item of items) {
            if (merged[item.name]) {
              merged[item.name].allocated += item.allocated
              merged[item.name].spent += item.spent
            } else {
              merged[item.name] = { ...item }
            }
          }
        }
        const sorted = Object.values(merged)
          .filter((d) => d.allocated > 0 || d.spent > 0)
          .sort((a, b) => b.spent - a.spent)
          .slice(0, 8)
        setData(sorted)
      })
      .catch(() => setData([]))
      .finally(() => setIsLoading(false))
  }, [ledgers, defaultCurrency])

  const chartHeight = Math.max(200, data.length * 50)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.charts.budgetUtilization")}</CardTitle>
        <CardDescription>{t("dashboard.charts.budgetUtilizationDescription")}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[300px] w-full" />
        ) : data.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t("dashboard.charts.noData")}
          </p>
        ) : (
          <ResponsiveContainer width="100%" height={chartHeight}>
            <BarChart data={data} layout="vertical">
              <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
              <XAxis type="number" className="text-xs" tickFormatter={(v) => formatCurrency(v, defaultCurrency)} />
              <YAxis type="category" dataKey="name" className="text-xs" width={100} />
              <Tooltip
                formatter={(value) => formatCurrency(Number(value), defaultCurrency)}
                contentStyle={{ borderRadius: "8px", border: "1px solid var(--color-border)", background: "var(--color-card)", color: "var(--color-card-foreground)" }}
              />
              <Legend />
              <Bar dataKey="allocated" name={t("dashboard.charts.allocated")} fill="var(--color-chart-4)" radius={[0, 4, 4, 0]} />
              <Bar dataKey="spent" name={t("dashboard.charts.spent")} fill="var(--color-chart-1)" radius={[0, 4, 4, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  )
}
