import { useMemo } from "react"
import { useQueries } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { ChartCard } from "@/components/charts/ChartCard"
import { CHART_COLORS, CHART_TOOLTIP_CONTENT_STYLE } from "@/components/charts/chart-theme"
import { getPortfolio } from "@/api/instruments"
import { instrumentKeys } from "@/api/instruments.hooks"
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

export function PortfolioAllocationChart({ ledgers, defaultCurrency }: Props) {
  const { t } = useTranslation()

  const portfolioQueries = useQueries({
    queries: ledgers.map((l) => ({
      queryKey: instrumentKeys.portfolio(l.attributes.slug),
      queryFn: () => getPortfolio(l.attributes.slug),
    })),
  })

  const isLoading = portfolioQueries.some((q) => q.isLoading)

  const data = useMemo<SliceData[]>(() => {
    const totals: Record<string, number> = {}
    for (const query of portfolioQueries) {
      if (!query.data) continue
      for (const pos of query.data.data) {
        const { instrumentType, currentValue, totalCost } = pos.attributes
        const value = currentValue ?? totalCost
        totals[instrumentType] = (totals[instrumentType] || 0) + value
      }
    }

    return Object.entries(totals)
      .map(([type, value]) => ({
        name: t(`instruments.types.${type}`),
        value,
      }))
      .filter((s) => s.value > 0)
      .sort((a, b) => b.value - a.value)
  }, [portfolioQueries, t])

  return (
    <ChartCard
      title={t("dashboard.charts.portfolioAllocation")}
      description={t("dashboard.charts.portfolioAllocationDescription")}
      isLoading={isLoading}
      isEmpty={data.length === 0}
      emptyLabel={t("dashboard.charts.noData")}
    >
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
              <Cell key={index} fill={CHART_COLORS[index % CHART_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value) => formatCurrency(Number(value), defaultCurrency)}
            contentStyle={CHART_TOOLTIP_CONTENT_STYLE}
          />
          <Legend />
        </PieChart>
      </ResponsiveContainer>
    </ChartCard>
  )
}
