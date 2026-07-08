import { useEffect, useState, useMemo } from "react"
import { useParams, Link, useNavigate } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useAuth } from "@/contexts/AuthContext"
import { useLedgers } from "@/api/ledgers.hooks"
import { useAccounts } from "@/api/accounts.hooks"
import { useTransactions } from "@/api/transactions.hooks"
import type { AccountResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { ArrowLeft, ArrowLeftRight, ChevronRight, Plus, ChevronDown, ChevronLeft, ChevronsLeft, ChevronsRight } from "lucide-react"
import { useErrorHandler } from "@/hooks/use-error-handler"

// Build breadcrumb path for an account
function buildAccountBreadcrumb(
  targetAccountId: string,
  accountList: AccountResource[]
): string[] {
  const accountMap = new Map<string, AccountResource>()
  accountList.forEach((acct: AccountResource) => {
    accountMap.set(acct.id, acct)
  })

  const breadcrumb: string[] = []
  let currentId: string | undefined = targetAccountId

  while (currentId && accountMap.has(currentId)) {
    const acct: AccountResource = accountMap.get(currentId)!
    breadcrumb.unshift(acct.attributes.name)
    currentId = acct.attributes.parentAccountId || undefined
  }

  return breadcrumb
}

export function AccountTransactions() {
  const { t } = useTranslation()
  const { ledgerSlug, accountId } = useParams<{ ledgerSlug: string; accountId: string }>()
  const { isAuthenticated } = useAuth()
  const handleError = useErrorHandler()
  const navigate = useNavigate()
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)

  // Server state: TanStack Query owns fetching, caching, and loading flags.
  // Gate every query on auth + route params so nothing fires until they exist.
  const enabled = isAuthenticated && !!ledgerSlug && !!accountId
  const {
    data: accountsResp,
    isLoading: accountsLoading,
    isError: accountsError,
    error: accountsErr,
  } = useAccounts(ledgerSlug ?? "", undefined, enabled)
  const {
    data: ledgersResp,
    isLoading: ledgersLoading,
    isError: ledgersError,
    error: ledgersErr,
  } = useLedgers(enabled)
  const {
    data: txResp,
    isLoading: txLoading,
    isError: txError,
    error: txErr,
  } = useTransactions(
    ledgerSlug ?? "",
    { accountId, "page[number]": currentPage, "page[size]": pageSize },
    enabled
  )

  const accounts = useMemo(() => accountsResp?.data ?? [], [accountsResp])
  const transactions = txResp?.data ?? []
  const ledger = ledgersResp?.data.find((l) => l.attributes.slug === ledgerSlug) ?? null
  const totalPages = txResp?.meta?.page?.totalPages ?? 0
  const totalElements = txResp?.meta?.page?.totalElements ?? 0
  const isLoading = accountsLoading || ledgersLoading || txLoading
  const isError = accountsError || ledgersError || txError
  const error = accountsErr ?? ledgersErr ?? txErr

  const account = useMemo(
    () => accounts.find((a) => a.id === accountId),
    [accounts, accountId]
  )

  const breadcrumb = useMemo(
    () => (accountId ? buildAccountBreadcrumb(accountId, accounts) : []),
    [accountId, accounts]
  )

  const handleCreateTransaction = (entryType: "DEBIT" | "CREDIT") => {
    if (!ledgerSlug || !account) return
    navigate(`/ledgers/${ledgerSlug}/transactions`, {
      state: {
        createTransaction: true,
        prefilledEntry: {
          accountId: account.id,
          currency: account.attributes.currency,
          type: entryType,
        },
      },
    })
  }

  // Reset to first page when switching accounts
  useEffect(() => {
    setCurrentPage(0)
  }, [accountId])

  // Surface fetch failures as a toast.
  useEffect(() => {
    if (isError) handleError(error, "fetchFailed")
  }, [isError, error, handleError])

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignIn", { resource: t("nav.transactions").toLowerCase() })}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to={`/ledgers/${ledgerSlug}/accounts`}>
            <Button variant="outline" size="icon">
              <ArrowLeft className="h-4 w-4" />
            </Button>
          </Link>
          <div>
            <div className="flex items-center gap-1 text-sm text-muted-foreground mb-1">
              {breadcrumb.map((part, index) => (
                <span key={index} className="flex items-center gap-1">
                  {index > 0 && <ChevronRight className="h-3 w-3" />}
                  <span className={index === breadcrumb.length - 1 ? "text-foreground font-medium" : ""}>
                    {part}
                  </span>
                </span>
              ))}
            </div>
            <h2 className="text-2xl font-bold tracking-tight">
              {account ? t("accountTransactions.title", { name: account.attributes.name }) : t("transactions.title")}
            </h2>
            <p className="text-muted-foreground">
              {ledger && `${ledger.attributes.name} - `}
              {t("accountTransactions.subtitle")}
            </p>
          </div>
        </div>
        {account && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button>
                <Plus className="mr-2 h-4 w-4" />
                {t("transactions.newTransaction")}
                <ChevronDown className="ml-2 h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => handleCreateTransaction("DEBIT")}>
                {t("accountTransactions.newDebit")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleCreateTransaction("CREDIT")}>
                {t("accountTransactions.newCredit")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("transactions.title")}</CardTitle>
          <CardDescription>
            {t("accountTransactions.transactionsFound", { count: totalElements })}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : transactions.length > 0 ? (
            <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.date")}</TableHead>
                  <TableHead>{t("common.description")}</TableHead>
                  <TableHead>{t("transactions.entries")}</TableHead>
                  <TableHead className="text-right">{t("common.amount")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map((transaction) => (
                  <TableRow key={transaction.id}>
                    <TableCell className="font-medium">
                      {formatDate(transaction.attributes.date)}
                    </TableCell>
                    <TableCell>{transaction.attributes.description}</TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {transaction.attributes.entries?.map((entry, idx) => (
                          <Badge
                            key={idx}
                            variant={
                              entry.accountId === accountId
                                ? entry.type === "DEBIT"
                                  ? "default"
                                  : "secondary"
                                : "outline"
                            }
                          >
                            {entry.accountName}: {entry.type === "DEBIT" ? "DB" : "CR"}
                          </Badge>
                        ))}
                      </div>
                    </TableCell>
                    <TableCell className="text-right font-mono">
                      {formatCurrency(
                        transaction.attributes.amount,
                        transaction.attributes.currency
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {totalPages > 1 && (
              <div className="flex items-center justify-between pt-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                  <span>{t("transactions.totalTransactions", { count: totalElements })}</span>
                  <span className="text-muted-foreground/50">·</span>
                  <Select value={String(pageSize)} onValueChange={(value) => { setPageSize(Number(value)); setCurrentPage(0) }}>
                    <SelectTrigger className="h-8 w-[70px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="10">10</SelectItem>
                      <SelectItem value="20">20</SelectItem>
                      <SelectItem value="50">50</SelectItem>
                    </SelectContent>
                  </Select>
                  <span>{t("transactions.perPage")}</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-sm text-muted-foreground mr-2">
                    {t("transactions.page", { current: currentPage + 1, total: totalPages })}
                  </span>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage === 0} onClick={() => setCurrentPage(0)}>
                    <ChevronsLeft className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage === 0} onClick={() => setCurrentPage(currentPage - 1)}>
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(currentPage + 1)}>
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="icon" className="h-8 w-8" disabled={currentPage >= totalPages - 1} onClick={() => setCurrentPage(totalPages - 1)}>
                    <ChevronsRight className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            )}
            </>
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">{t("accountTransactions.noTransactions")}</h3>
              <p className="text-sm text-muted-foreground mb-4">
                {t("accountTransactions.noTransactionsDescription")}
              </p>
              <Link to={`/ledgers/${ledgerSlug}/transactions`}>
                <Button variant="outline">{t("accountTransactions.goToTransactions")}</Button>
              </Link>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
