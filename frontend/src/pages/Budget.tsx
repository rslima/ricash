import { useState, useMemo } from "react"
import { Link } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
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
import { useEnvelopes, useBudgetSummary, useAllocateEnvelope } from "@/api/envelopes.hooks"
import type { EnvelopeResource, EnvelopeBalance, EnvelopeType } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { ChevronLeft, ChevronRight, FolderOpen, TrendingUp, TrendingDown, PiggyBank } from "lucide-react"
import { cn } from "@/lib/utils"
import { useErrorHandler } from "@/hooks/use-error-handler"
import { SignInRequired } from "@/components/SignInRequired"
import { EmptyState } from "@/components/EmptyState"
import { TableSkeleton } from "@/components/ui/table-skeleton"
import { LedgerSelector } from "@/components/LedgerSelector"
import { useLedgerSelection } from "@/hooks/use-ledger-selection"
import { combineQueries, useQueryErrorToast } from "@/hooks/use-query-error-toast"
import { MONTH_KEYS } from "@/lib/dates"

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

export function Budget() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()

  const now = new Date()
  const [selectedYear, setSelectedYear] = useState(now.getFullYear())
  const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1)

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  const ledgerSelection = useLedgerSelection()
  const { ledgers, selectedLedgerSlug, setSelectedLedgerSlug, ledgerCurrency } = ledgerSelection

  const envelopesQuery = useEnvelopes(
    selectedLedgerSlug ?? "",
    { "page[size]": 200 },
    isAuthenticated && !!selectedLedgerSlug
  )
  const envelopes = useMemo(() => envelopesQuery.data?.data ?? [], [envelopesQuery.data])

  const budgetQuery = useBudgetSummary(
    selectedLedgerSlug ?? "",
    selectedYear,
    selectedMonth,
    isAuthenticated && !!selectedLedgerSlug
  )
  const balances = useMemo(() => budgetQuery.data?.envelopeBalances ?? [], [budgetQuery.data])
  const toBeBudgeted = budgetQuery.data?.toBeBudgeted ?? 0

  const { isLoading } = combineQueries(envelopesQuery, budgetQuery)

  const allocateEnvelopeMutation = useAllocateEnvelope(selectedLedgerSlug ?? "")

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

  // Surface fetch failures as a toast (mutations report their own errors inline).
  useQueryErrorToast([ledgerSelection, envelopesQuery, budgetQuery])

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

  const handleAllocate = (envelopeId: string, amount: number) => {
    if (!selectedLedgerSlug) return

    // The hook invalidates the budget/envelope keys on success, so no manual refetch.
    allocateEnvelopeMutation.mutate(
      {
        envelopeId,
        data: {
          year: selectedYear,
          month: selectedMonth,
          allocatedAmount: amount,
        },
      },
      {
        onError: (err) => handleError(err, "updateFailed"),
      }
    )
  }

  if (!isAuthenticated) {
    return <SignInRequired resourceKey="nav.budget" />
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

      <LedgerSelector
        ledgers={ledgers}
        selectedSlug={selectedLedgerSlug}
        onSelect={setSelectedLedgerSlug}
      />

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
              {MONTH_KEYS.map((month, index) => (
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
        <TableSkeleton />
      ) : !selectedLedgerSlug ? (
        <Card>
          <CardContent>
            <EmptyState
              icon={FolderOpen}
              title={t("budget.noLedgerSelected")}
              description={t("budget.selectLedgerDescription")}
            />
          </CardContent>
        </Card>
      ) : envelopes.length === 0 ? (
        <Card>
          <CardContent>
            <EmptyState
              icon={FolderOpen}
              title={t("budget.noEnvelopes")}
              description={t("budget.noEnvelopesDescription")}
              action={
                <Link to={`/ledgers/${selectedLedgerSlug}/envelopes`}>
                  <Button variant="outline">{t("budget.goToEnvelopes")}</Button>
                </Link>
              }
            />
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
