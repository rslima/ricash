import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from "recharts"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Button } from "@/components/ui/button"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { getAccounts } from "@/api/accounts"
import { getMonthlyExpenseBreakdown } from "@/api/transactions"
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

const OTHERS_SLICE_ID = "__others__"

type Mode = "all-time" | "monthly"

const MONTH_KEYS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December",
] as const

export function ExpenseBreakdownChart({ ledgers }: Props) {
  const { t } = useTranslation()
  const [allAccounts, setAllAccounts] = useState<AccountResource[]>([])
  const [isLoadingAccounts, setIsLoadingAccounts] = useState(true)
  const [drillParentId, setDrillParentId] = useState<string | null>(null)
  const [drillParentName, setDrillParentName] = useState<string | null>(null)
  const [othersCurrency, setOthersCurrency] = useState<string | null>(null)

  const now = useMemo(() => new Date(), [])
  const [mode, setMode] = useState<Mode>("all-time")
  const [selectedYear, setSelectedYear] = useState(now.getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1)
  const [monthlyExpenses, setMonthlyExpenses] = useState<Record<string, number>>({})
  const [isLoadingMonthly, setIsLoadingMonthly] = useState(false)

  useEffect(() => {
    Promise.all(
      ledgers.map((l) => getAccounts(l.attributes.slug, { "page[size]": 200 }))
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
      .finally(() => setIsLoadingAccounts(false))
  }, [ledgers])

  useEffect(() => {
    if (mode !== "monthly") return
    setIsLoadingMonthly(true)
    Promise.all(
      ledgers.map((l) => getMonthlyExpenseBreakdown(l.attributes.slug, selectedYear, selectedMonth))
    )
      .then((responses) => {
        const merged: Record<string, number> = {}
        for (const report of responses) {
          for (const [accountId, amount] of Object.entries(report.expensesByAccountId ?? {})) {
            merged[accountId] = (merged[accountId] ?? 0) + Number(amount)
          }
        }
        setMonthlyExpenses(merged)
      })
      .catch(() => setMonthlyExpenses({}))
      .finally(() => setIsLoadingMonthly(false))
  }, [ledgers, mode, selectedYear, selectedMonth])

  const valueForAccount = useCallback(
    (account: AccountResource): number => {
      if (mode === "monthly") return monthlyExpenses[account.id] ?? 0
      return account.attributes.balance
    },
    [mode, monthlyExpenses]
  )

  const buildSlices = useCallback(
    (currency: string): SliceData[] => {
      const filtered = allAccounts.filter((a) => {
        const matchesCurrency = a.attributes.currency === currency
        const matchesParent = drillParentId
          ? a.attributes.parentAccountId === drillParentId
          : a.attributes.parentAccountId === null
        return matchesCurrency && matchesParent && valueForAccount(a) > 0
      })

      const sorted = filtered
        .map((a) => ({ name: a.attributes.name, value: valueForAccount(a), accountId: a.id }))
        .sort((a, b) => b.value - a.value)

      if (othersCurrency === currency) return sorted.slice(7)

      if (sorted.length <= 8) return sorted

      const top = sorted.slice(0, 7)
      const otherValue = sorted.slice(7).reduce((sum, s) => sum + s.value, 0)
      top.push({ name: t("dashboard.charts.other"), value: otherValue, accountId: OTHERS_SLICE_ID })
      return top
    },
    [allAccounts, drillParentId, othersCurrency, t, valueForAccount]
  )

  const currencies = [...new Set(
    allAccounts
      .filter((a) =>
        (drillParentId ? a.attributes.parentAccountId === drillParentId : a.attributes.parentAccountId === null) &&
        valueForAccount(a) > 0
      )
      .map((a) => a.attributes.currency)
  )]
    .sort()
    .filter((c) => !othersCurrency || c === othersCurrency)

  const handleSliceClick = (accountId: string, accountName: string, currency: string) => {
    if (accountId === OTHERS_SLICE_ID) {
      setOthersCurrency(currency)
      return
    }
    if (!accountId) return
    const hasChildren = allAccounts.some((a) => a.attributes.parentAccountId === accountId && valueForAccount(a) > 0)
    if (hasChildren) {
      setDrillParentId(accountId)
      setDrillParentName(accountName)
      setOthersCurrency(null)
    }
  }

  const handleBack = () => {
    if (othersCurrency) {
      setOthersCurrency(null)
      return
    }
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

  const yearOptions = useMemo(() => {
    const current = now.getFullYear()
    const years = new Set<number>()
    for (let y = current - 5; y <= current + 1; y++) years.add(y)
    years.add(selectedYear)
    return [...years].sort((a, b) => a - b)
  }, [now, selectedYear])

  const isLoading = isLoadingAccounts || (mode === "monthly" && isLoadingMonthly)

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.charts.expenseBreakdown")}</CardTitle>
        <CardDescription>
          {othersCurrency
            ? drillParentName
              ? `${drillParentName} · ${t("dashboard.charts.other")}`
              : t("dashboard.charts.other")
            : drillParentName
            ? drillParentName
            : mode === "monthly"
            ? t("dashboard.charts.expenseBreakdownMonthlyDescription")
            : t("dashboard.charts.expenseBreakdownDescription")}
        </CardDescription>
        <div className="flex flex-wrap items-center gap-2 pt-1">
          <div className="inline-flex rounded-md border p-0.5">
            <Button
              variant={mode === "all-time" ? "secondary" : "ghost"}
              size="sm"
              className="h-7 px-3"
              onClick={() => setMode("all-time")}
            >
              {t("dashboard.charts.expenseBreakdownAllTime")}
            </Button>
            <Button
              variant={mode === "monthly" ? "secondary" : "ghost"}
              size="sm"
              className="h-7 px-3"
              onClick={() => setMode("monthly")}
            >
              {t("dashboard.charts.expenseBreakdownMonthly")}
            </Button>
          </div>
          {mode === "monthly" && (
            <>
              <Select
                value={selectedMonth.toString()}
                onValueChange={(v) => setSelectedMonth(parseInt(v))}
              >
                <SelectTrigger className="h-8 w-32">
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
              <Select
                value={selectedYear.toString()}
                onValueChange={(v) => setSelectedYear(parseInt(v))}
              >
                <SelectTrigger className="h-8 w-24">
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
            </>
          )}
        </div>
        {(drillParentId || othersCurrency) && (
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
                          handleSliceClick(slice.accountId, slice.name, currency)
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
