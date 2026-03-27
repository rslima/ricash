import { useEffect, useState, useMemo } from "react"
import { useParams, Link } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useAuth } from "@/contexts/AuthContext"
import { getEnvelopes, getBudgetSummary, allocateEnvelope } from "@/api/envelopes"
import { getLedgers } from "@/api/ledgers"
import type { EnvelopeResource, LedgerResource, EnvelopeBalance, EnvelopeType } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { ChevronLeft, ChevronRight, FolderOpen, TrendingUp, TrendingDown, PiggyBank } from "lucide-react"
import { cn } from "@/lib/utils"
import { useErrorHandler } from "@/hooks/use-error-handler"

interface BudgetProgressBarProps {
  spent: number
  allocated: number
  available: number
  currency: string
}

function BudgetProgressBar({ spent, allocated, available, currency }: BudgetProgressBarProps) {
  const percent = allocated > 0 ? Math.min((spent / allocated) * 100, 100) : 0
  const isOverBudget = spent > allocated
  const isNearLimit = percent >= 80 && percent < 100

  return (
    <div className="w-full">
      <div className="h-2 bg-muted rounded-full overflow-hidden">
        <div
          className={cn(
            "h-full transition-all duration-300",
            isOverBudget
              ? "bg-destructive"
              : isNearLimit
              ? "bg-yellow-500"
              : "bg-primary"
          )}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>
      <div className="flex justify-between text-xs text-muted-foreground mt-1">
        <span>{formatCurrency(spent, currency)} / {formatCurrency(allocated, currency)}</span>
        <span
          className={cn(
            "font-medium",
            available < 0 ? "text-destructive" : "text-primary"
          )}
        >
          {formatCurrency(available, currency)} {available >= 0 ? "left" : "over"}
        </span>
      </div>
    </div>
  )
}

interface EnvelopeBudgetRowProps {
  envelope: EnvelopeResource
  balance: EnvelopeBalance | undefined
  onAllocate: (envelopeId: string, amount: number) => void
  t: (key: string) => string
}

function EnvelopeBudgetRow({ envelope, balance, onAllocate }: EnvelopeBudgetRowProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [editValue, setEditValue] = useState("")

  const allocated = balance?.allocated ?? 0
  const spent = balance?.spent ?? 0
  const available = balance?.available ?? 0
  const rollover = balance?.rollover ?? 0

  const handleEditStart = () => {
    setEditValue(allocated.toString())
    setIsEditing(true)
  }

  const handleEditSave = () => {
    const amount = parseFloat(editValue) || 0
    onAllocate(envelope.id, amount)
    setIsEditing(false)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      handleEditSave()
    } else if (e.key === "Escape") {
      setIsEditing(false)
    }
  }

  return (
    <TableRow>
      <TableCell className="font-medium">
        <div className="flex items-center gap-2">
          <FolderOpen className="h-4 w-4 text-muted-foreground" />
          {envelope.attributes.name}
        </div>
      </TableCell>
      <TableCell className="text-right font-mono text-muted-foreground">
        {rollover > 0 ? formatCurrency(rollover, envelope.attributes.currency) : "-"}
      </TableCell>
      <TableCell className="text-right">
        {isEditing ? (
          <Input
            type="number"
            value={editValue}
            onChange={(e) => setEditValue(e.target.value)}
            onBlur={handleEditSave}
            onKeyDown={handleKeyDown}
            className="w-24 h-8 text-right"
            autoFocus
          />
        ) : (
          <Button
            variant="ghost"
            size="sm"
            className="font-mono h-8 px-2"
            onClick={handleEditStart}
          >
            {formatCurrency(allocated, envelope.attributes.currency)}
          </Button>
        )}
      </TableCell>
      <TableCell className="text-right font-mono">
        {formatCurrency(spent, envelope.attributes.currency)}
      </TableCell>
      <TableCell className="text-right font-mono">
        <span className={cn(available < 0 ? "text-destructive" : "text-primary")}>
          {formatCurrency(available, envelope.attributes.currency)}
        </span>
      </TableCell>
      <TableCell className="w-48">
        <BudgetProgressBar
          spent={spent}
          allocated={allocated}
          available={available}
          currency={envelope.attributes.currency}
        />
      </TableCell>
    </TableRow>
  )
}

const MONTHS = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
]

export function Budget() {
  const { t } = useTranslation()
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const [envelopes, setEnvelopes] = useState<EnvelopeResource[]>([])
  const [balances, setBalances] = useState<EnvelopeBalance[]>([])
  const [toBeBudgeted, setToBeBudgeted] = useState(0)
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isLoading, setIsLoading] = useState(true)

  const now = new Date()
  const [selectedYear, setSelectedYear] = useState(now.getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1)

  const envelopesByType = useMemo(() => {
    const grouped: Record<EnvelopeType, EnvelopeResource[]> = {
      INCOME: [],
      EXPENSE: [],
    }

    envelopes.forEach((envelope) => {
      // Only show top-level envelopes (no parent)
      if (!envelope.attributes.parentEnvelopeId) {
        grouped[envelope.attributes.type].push(envelope)
      }
    })

    return grouped
  }, [envelopes])

  const balanceMap = useMemo(() => {
    const map = new Map<string, EnvelopeBalance>()
    balances.forEach((balance) => {
      map.set(balance.envelopeId, balance)
    })
    return map
  }, [balances])

  useEffect(() => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    getLedgers()
      .then((response) => {
        setLedgers(response.data)
        if (!selectedLedgerSlug && response.data.length > 0) {
          setSelectedLedgerSlug(response.data[0].attributes.slug)
        }
      })
      .catch((e) => handleError(e, "fetchFailed"))
  }, [isAuthenticated])

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      getEnvelopes(selectedLedgerSlug, { "page[size]": 200 }),
      getBudgetSummary(selectedLedgerSlug, selectedYear, selectedMonth),
    ])
      .then(([envelopesResponse, budgetResponse]) => {
        setEnvelopes(envelopesResponse.data)
        setBalances(budgetResponse.envelopeBalances)
        setToBeBudgeted(budgetResponse.toBeBudgeted)
      })
      .catch((e) => handleError(e, "fetchFailed"))
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, selectedYear, selectedMonth, isAuthenticated])

  const handlePreviousMonth = () => {
    if (selectedMonth === 1) {
      setSelectedMonth(12)
      setSelectedYear(selectedYear - 1)
    } else {
      setSelectedMonth(selectedMonth - 1)
    }
  }

  const handleNextMonth = () => {
    if (selectedMonth === 12) {
      setSelectedMonth(1)
      setSelectedYear(selectedYear + 1)
    } else {
      setSelectedMonth(selectedMonth + 1)
    }
  }

  const handleAllocate = async (envelopeId: string, amount: number) => {
    if (!selectedLedgerSlug) return

    try {
      await allocateEnvelope(selectedLedgerSlug, envelopeId, {
        year: selectedYear,
        month: selectedMonth,
        allocatedAmount: amount,
      })

      // Refresh budget summary
      const budgetResponse = await getBudgetSummary(selectedLedgerSlug, selectedYear, selectedMonth)
      setBalances(budgetResponse.envelopeBalances)
      setToBeBudgeted(budgetResponse.toBeBudgeted)
    } catch (error) {
      handleError(error, "updateFailed")
    }
  }

  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)
  const ledgerCurrency = selectedLedger?.attributes.currency ?? "BRL"

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.budget").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t("budget.title")}</h2>
          <p className="text-muted-foreground">
            {t("budget.subtitle")}
          </p>
        </div>
      </div>

      {ledgers.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {ledgers.map((ledger) => (
            <Button
              key={ledger.id}
              variant={selectedLedgerSlug === ledger.attributes.slug ? "default" : "outline"}
              size="sm"
              onClick={() => setSelectedLedgerSlug(ledger.attributes.slug)}
            >
              {ledger.attributes.name}
            </Button>
          ))}
        </div>
      )}

      {/* Month Selector */}
      <div className="flex items-center justify-center gap-4">
        <Button variant="outline" size="icon" onClick={handlePreviousMonth}>
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <div className="flex items-center gap-2">
          <Select
            value={selectedMonth.toString()}
            onValueChange={(value) => setSelectedMonth(parseInt(value))}
          >
            <SelectTrigger className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {MONTHS.map((month, index) => (
                <SelectItem key={month} value={(index + 1).toString()}>
                  {t(`budget.months.${month}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={selectedYear.toString()}
            onValueChange={(value) => setSelectedYear(parseInt(value))}
          >
            <SelectTrigger className="w-24">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[selectedYear - 1, selectedYear, selectedYear + 1].map((year) => (
                <SelectItem key={year} value={year.toString()}>
                  {year}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button variant="outline" size="icon" onClick={handleNextMonth}>
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>

      {/* To Be Budgeted Card */}
      <Card className={cn(
        "border-2",
        toBeBudgeted > 0 ? "border-primary bg-primary/5" : toBeBudgeted < 0 ? "border-destructive bg-destructive/5" : ""
      )}>
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <PiggyBank className="h-5 w-5" />
              <CardTitle className="text-lg">{t("budget.toBeBudgeted")}</CardTitle>
            </div>
            <span className={cn(
              "text-2xl font-bold",
              toBeBudgeted > 0 ? "text-primary" : toBeBudgeted < 0 ? "text-destructive" : ""
            )}>
              {formatCurrency(toBeBudgeted, ledgerCurrency)}
            </span>
          </div>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            {toBeBudgeted > 0
              ? t("budget.moneyToAssign")
              : toBeBudgeted < 0
              ? t("budget.overbudgeted")
              : t("budget.fullyAllocated")}
          </p>
        </CardContent>
      </Card>

      {/* Envelopes by Type */}
      {isLoading ? (
        <div className="space-y-3">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
        </div>
      ) : !selectedLedgerSlug ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <FolderOpen className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-semibold">{t("budget.noLedgerSelected")}</h3>
            <p className="text-sm text-muted-foreground mb-4">
              {t("budget.selectLedgerDescription")}
            </p>
          </CardContent>
        </Card>
      ) : envelopes.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12">
            <FolderOpen className="h-12 w-12 text-muted-foreground mb-4" />
            <h3 className="text-lg font-semibold">{t("budget.noEnvelopes")}</h3>
            <p className="text-sm text-muted-foreground mb-4">
              {t("budget.noEnvelopesDescription")}
            </p>
            <Link to={`/ledgers/${selectedLedgerSlug}/envelopes`}>
              <Button variant="outline">{t("budget.goToEnvelopes")}</Button>
            </Link>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-6">
          {/* Income Targets */}
          {envelopesByType.INCOME.length > 0 && (
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <TrendingUp className="h-5 w-5 text-primary" />
                  <CardTitle>{t("budget.incomeTargets")}</CardTitle>
                </div>
                <CardDescription>{t("budget.incomeTargetsDescription")}</CardDescription>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t("common.name")}</TableHead>
                      <TableHead className="text-right">{t("budget.rollover")}</TableHead>
                      <TableHead className="text-right">{t("budget.allocated")}</TableHead>
                      <TableHead className="text-right">{t("budget.received")}</TableHead>
                      <TableHead className="text-right">{t("budget.available")}</TableHead>
                      <TableHead>{t("budget.progress")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {envelopesByType.INCOME.map((envelope) => (
                      <EnvelopeBudgetRow
                        key={envelope.id}
                        envelope={envelope}
                        balance={balanceMap.get(envelope.id)}
                        onAllocate={handleAllocate}
                        t={t}
                      />
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Expense Limits */}
          {envelopesByType.EXPENSE.length > 0 && (
            <Card>
              <CardHeader>
                <div className="flex items-center gap-2">
                  <TrendingDown className="h-5 w-5 text-destructive" />
                  <CardTitle>{t("budget.expenseLimits")}</CardTitle>
                </div>
                <CardDescription>{t("budget.expenseLimitsDescription")}</CardDescription>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t("common.name")}</TableHead>
                      <TableHead className="text-right">{t("budget.rollover")}</TableHead>
                      <TableHead className="text-right">{t("budget.allocated")}</TableHead>
                      <TableHead className="text-right">{t("budget.spent")}</TableHead>
                      <TableHead className="text-right">{t("budget.available")}</TableHead>
                      <TableHead>{t("budget.progress")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {envelopesByType.EXPENSE.map((envelope) => (
                      <EnvelopeBudgetRow
                        key={envelope.id}
                        envelope={envelope}
                        balance={balanceMap.get(envelope.id)}
                        onAllocate={handleAllocate}
                        t={t}
                      />
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  )
}
