import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Button } from "@/components/ui/button"
import { getAccounts } from "@/api/accounts"
import type { LedgerResource, AccountResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { ArrowLeft } from "lucide-react"

interface Props {
  ledgers: LedgerResource[]
}

interface SliceData {
  name: string
  value: number
  accountId: string
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

export function ExpenseBreakdownChart({ ledgers }: Props) {
  const { t } = useTranslation()
  const [allAccounts, setAllAccounts] = useState<AccountResource[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [drillParentId, setDrillParentId] = useState<string | null>(null)
  const [drillParentName, setDrillParentName] = useState<string | null>(null)

  useEffect(() => {
    Promise.all(
      ledgers.map((l) => getAccounts(l.attributes.slug, { "page[size]": 100 }))
    )
      .then((responses) => {
        const accounts: AccountResource[] = []
        for (const res of responses) {
          for (const account of res.data) {
            if (account.attributes.type === "EXPENSE") {
              accounts.push(account)
            }
          }
        }
        setAllAccounts(accounts)
      })
      .catch(() => setAllAccounts([]))
      .finally(() => setIsLoading(false))
  }, [ledgers])

  const buildSlices = useCallback(
    (currency: string): SliceData[] => {
      const filtered = allAccounts.filter((a) => {
        const matchesCurrency = a.attributes.currency === currency
        const matchesParent = drillParentId
          ? a.attributes.parentAccountId === drillParentId
          : a.attributes.parentAccountId === null
        return matchesCurrency && matchesParent && a.attributes.balance > 0
      })

      const sorted = filtered
        .map((a) => ({ name: a.attributes.name, value: a.attributes.balance, accountId: a.id }))
        .sort((a, b) => b.value - a.value)

      if (sorted.length <= 8) return sorted

      const top = sorted.slice(0, 7)
      const otherValue = sorted.slice(7).reduce((sum, s) => sum + s.value, 0)
      top.push({ name: t("dashboard.charts.other"), value: otherValue, accountId: "" })
      return top
    },
    [allAccounts, drillParentId, t]
  )

  const currencies = [...new Set(
    allAccounts
      .filter((a) =>
        (drillParentId ? a.attributes.parentAccountId === drillParentId : a.attributes.parentAccountId === null) &&
        a.attributes.balance > 0
      )
      .map((a) => a.attributes.currency)
  )].sort()

  const handleSliceClick = (accountId: string, accountName: string) => {
    if (!accountId) return
    const hasChildren = allAccounts.some((a) => a.attributes.parentAccountId === accountId && a.attributes.balance > 0)
    if (hasChildren) {
      setDrillParentId(accountId)
      setDrillParentName(accountName)
    }
  }

  const handleBack = () => {
    if (!drillParentId) return
    const parent = allAccounts.find((a) => a.id === drillParentId)
    if (parent?.attributes.parentAccountId) {
      const grandparent = allAccounts.find((a) => a.id === parent.attributes.parentAccountId)
      setDrillParentId(parent.attributes.parentAccountId)
      setDrillParentName(grandparent?.attributes.name ?? null)
    } else {
      setDrillParentId(null)
      setDrillParentName(null)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.charts.expenseBreakdown")}</CardTitle>
        <CardDescription>
          {drillParentName
            ? drillParentName
            : t("dashboard.charts.expenseBreakdownDescription")}
        </CardDescription>
        {drillParentId && (
          <Button variant="ghost" size="sm" className="w-fit gap-1" onClick={handleBack}>
            <ArrowLeft className="h-4 w-4" />
            {t("dashboard.charts.backToAll")}
          </Button>
        )}
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[300px] w-full" />
        ) : currencies.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-8">
            {t("dashboard.charts.noData")}
          </p>
        ) : (
          <div className="space-y-6">
            {currencies.map((currency) => {
              const slices = buildSlices(currency)
              if (slices.length === 0) return null
              return (
                <div key={currency}>
                  {currencies.length > 1 && (
                    <p className="text-sm font-medium text-muted-foreground mb-2">{currency}</p>
                  )}
                  <ResponsiveContainer width="100%" height={300}>
                    <PieChart>
                      <Pie
                        data={slices}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={100}
                        dataKey="value"
                        paddingAngle={2}
                        style={{ cursor: "pointer" }}
                        onClick={(_, index) => {
                          const slice = slices[index]
                          handleSliceClick(slice.accountId, slice.name)
                        }}
                      >
                        {slices.map((_, index) => (
                          <Cell key={index} fill={COLORS[index % COLORS.length]} />
                        ))}
                      </Pie>
                      <Tooltip
                        formatter={(value) => formatCurrency(Number(value), currency)}
                        contentStyle={{ borderRadius: "8px", border: "1px solid var(--color-border)", background: "var(--color-card)", color: "var(--color-card-foreground)" }}
                      />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )
            })}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
