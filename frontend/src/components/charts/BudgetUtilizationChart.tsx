import { useMemo } from "react"
import { useQueries } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { ChartCard } from "@/components/charts/ChartCard"
import { CHART_COLORS, CHART_TOOLTIP_CONTENT_STYLE } from "@/components/charts/chart-theme"
import { getBudgetSummary, getEnvelopes } from "@/api/envelopes"
import { envelopeKeys } from "@/api/envelopes.hooks"
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

  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth() + 1

  // Per-ledger fan-out; keys mirror the envelope hooks so cache entries and
  // ledger-level invalidations stay in sync with the rest of the app.
  const budgetQueries = useQueries({
    queries: ledgers.map((l) => ({
      queryKey: envelopeKeys.budget(l.attributes.slug, year, month),
      queryFn: () => getBudgetSummary(l.attributes.slug, year, month),
    })),
  })

  const envelopeQueries = useQueries({
    queries: ledgers.map((l) => ({
      queryKey: envelopeKeys.list(l.attributes.slug, { "page[size]": 100 }),
      queryFn: () => getEnvelopes(l.attributes.slug, { "page[size]": 100 }),
    })),
  })

  const isLoading =
    budgetQueries.some((q) => q.isLoading) || envelopeQueries.some((q) => q.isLoading)

  const data = useMemo(() => {
    const merged: Record<string, EnvelopeData> = {}
    ledgers.forEach((_, index) => {
      const budget = budgetQueries[index]?.data
      const envelopesRes = envelopeQueries[index]?.data
      if (!budget || !envelopesRes) return

      const nameMap = new Map<string, { name: string; type: string; currency: string }>()
      for (const env of envelopesRes.data) {
        nameMap.set(env.id, {
          name: env.attributes.name,
          type: env.attributes.type,
          currency: env.attributes.currency,
        })
      }

      for (const b of budget.envelopeBalances) {
        const info = nameMap.get(b.envelopeId)
        if (!info || info.type !== "EXPENSE" || info.currency !== defaultCurrency) continue
        const existing = merged[info.name]
        if (existing) {
          existing.allocated += b.allocated
          existing.spent += b.spent
        } else {
          merged[info.name] = { name: info.name, allocated: b.allocated, spent: b.spent }
        }
      }
    })
    return Object.values(merged)
      .filter((d) => d.allocated > 0 || d.spent > 0)
      .sort((a, b) => b.spent - a.spent)
      .slice(0, 8)
  }, [ledgers, budgetQueries, envelopeQueries, defaultCurrency])

  const chartHeight = Math.max(200, data.length * 50)

  return (
    <ChartCard
      title={t("dashboard.charts.budgetUtilization")}
      description={t("dashboard.charts.budgetUtilizationDescription")}
      isLoading={isLoading}
      isEmpty={data.length === 0}
      emptyLabel={t("dashboard.charts.noData")}
    >
      <ResponsiveContainer width="100%" height={chartHeight}>
        <BarChart data={data} layout="vertical">
          <CartesianGrid strokeDasharray="3 3" className="stroke-border" />
          <XAxis type="number" className="text-xs" tickFormatter={(v) => formatCurrency(v, defaultCurrency)} />
          <YAxis type="category" dataKey="name" className="text-xs" width={100} />
          <Tooltip
            formatter={(value) => formatCurrency(Number(value), defaultCurrency)}
            contentStyle={CHART_TOOLTIP_CONTENT_STYLE}
          />
          <Legend />
          <Bar dataKey="allocated" name={t("dashboard.charts.allocated")} fill={CHART_COLORS[3]} radius={[0, 4, 4, 0]} />
          <Bar dataKey="spent" name={t("dashboard.charts.spent")} fill={CHART_COLORS[0]} radius={[0, 4, 4, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
