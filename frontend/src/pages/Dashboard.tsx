import { useEffect, useState, useMemo } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"
import { getLedgers } from "@/api/ledgers"
import { getAccounts } from "@/api/accounts"
import { getTransactions } from "@/api/transactions"
import type { LedgerResource, AccountResource, TransactionResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { Wallet, ArrowUpRight, ArrowDownRight, BookOpen } from "lucide-react"

export function Dashboard() {
  const { t } = useTranslation()
  const { isAuthenticated, user } = useAuth()
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [defaultCurrency, setDefaultCurrency] = useState("BRL")

  useEffect(() => {
    if (isAuthenticated) {
      getLedgers()
        .then(async (response) => {
          setLedgers(response.data)

          if (response.data.length > 0) {
            // Use first ledger's currency as default
            setDefaultCurrency(response.data[0].attributes.currency)

            // Fetch accounts and transactions from all ledgers
            const allAccounts: AccountResource[] = []
            const allTransactions: TransactionResource[] = []

            await Promise.all(
              response.data.map(async (ledger) => {
                const [accountsRes, transactionsRes] = await Promise.all([
                  getAccounts(ledger.attributes.slug),
                  // Fetch more transactions to have enough for monthly calculations
                  getTransactions(ledger.attributes.slug, { "page[size]": 100 }),
                ])
                allAccounts.push(...accountsRes.data)
                allTransactions.push(...transactionsRes.data)
              })
            )

            setAccounts(allAccounts)
            // Sort transactions by date descending
            allTransactions.sort((a, b) =>
              new Date(b.attributes.date).getTime() - new Date(a.attributes.date).getTime()
            )
            setTransactions(allTransactions)
          }
        })
        .catch(console.error)
        .finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [isAuthenticated])

  // Calculate total balance (sum of leaf ASSET accounts minus leaf LIABILITY accounts)
  // Leaf accounts are accounts that have no children
  const totalBalance = useMemo(() => {
    // Find all account IDs that are parents (have children)
    const parentIds = new Set<string>()
    accounts.forEach((account) => {
      if (account.attributes.parentAccountId) {
        parentIds.add(account.attributes.parentAccountId)
      }
    })

    // Only sum leaf accounts (accounts that are not parents)
    let balance = 0
    accounts.forEach((account) => {
      const isLeaf = !parentIds.has(account.id)
      if (!isLeaf) return

      const type = account.attributes.type
      const accountBalance = account.attributes.balance || 0
      if (type === "ASSET") {
        balance += accountBalance
      } else if (type === "LIABILITY") {
        balance -= accountBalance
      }
    })
    return balance
  }, [accounts])

  // Calculate monthly income and expenses from transactions
  const { monthlyIncome, monthlyExpenses } = useMemo(() => {
    const now = new Date()
    const currentMonth = now.getMonth()
    const currentYear = now.getFullYear()

    let income = 0
    let expenses = 0

    // We need to look at account types to determine if it's income or expense
    const accountTypeMap = new Map<string, string>()
    accounts.forEach((account) => {
      accountTypeMap.set(account.id, account.attributes.type)
    })

    transactions.forEach((transaction) => {
      const transactionDate = new Date(transaction.attributes.date)
      if (transactionDate.getMonth() === currentMonth && transactionDate.getFullYear() === currentYear) {
        transaction.attributes.entries?.forEach((entry) => {
          const accountType = accountTypeMap.get(entry.accountId)
          // Income: credit to INCOME account
          if (accountType === "INCOME" && entry.type === "CREDIT") {
            income += entry.amount
          }
          // Expense: debit to EXPENSE account
          if (accountType === "EXPENSE" && entry.type === "DEBIT") {
            expenses += entry.amount
          }
        })
      }
    })

    return { monthlyIncome: income, monthlyExpenses: expenses }
  }, [transactions, accounts])

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("dashboard.welcomeTitle")}</CardTitle>
            <CardDescription>
              {t("dashboard.welcomeDescription")}
            </CardDescription>
          </CardHeader>
          <CardContent className="text-center">
            <p className="text-muted-foreground">
              {t("dashboard.welcomeText")}
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">
          {t("dashboard.welcome", { name: user?.name })}
        </h2>
        <p className="text-muted-foreground">
          {t("dashboard.overview")}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.totalLedgers")}</CardTitle>
            <BookOpen className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-16" />
            ) : (
              <div className="text-2xl font-bold">{ledgers.length}</div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.totalBalance")}</CardTitle>
            <Wallet className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="text-2xl font-bold">{formatCurrency(totalBalance, defaultCurrency)}</div>
            )}
            <p className="text-xs text-muted-foreground">{t("dashboard.acrossAllAccounts")}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.income")}</CardTitle>
            <ArrowUpRight className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="text-2xl font-bold text-green-600">
                {formatCurrency(monthlyIncome, defaultCurrency)}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("dashboard.thisMonth")}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">{t("dashboard.expenses")}</CardTitle>
            <ArrowDownRight className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="text-2xl font-bold text-red-600">
                {formatCurrency(monthlyExpenses, defaultCurrency)}
              </div>
            )}
            <p className="text-xs text-muted-foreground">{t("dashboard.thisMonth")}</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t("dashboard.recentTransactions")}</CardTitle>
            <CardDescription>{t("dashboard.latestActivities")}</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : transactions.length > 0 ? (
              <div className="space-y-3">
                {transactions.slice(0, 5).map((transaction) => (
                  <div
                    key={transaction.id}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div className="flex-1 min-w-0">
                      <p className="font-medium truncate">{transaction.attributes.description}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatDate(transaction.attributes.date)}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 ml-2">
                      <div className="flex flex-wrap gap-1 justify-end">
                        {transaction.attributes.entries?.slice(0, 2).map((entry, idx) => (
                          <Badge
                            key={idx}
                            variant={entry.type === "DEBIT" ? "secondary" : "outline"}
                            className="text-xs"
                          >
                            {entry.type === "DEBIT" ? "DB" : "CR"}
                          </Badge>
                        ))}
                      </div>
                      <span className="font-mono text-sm font-medium">
                        {formatCurrency(transaction.attributes.amount, transaction.attributes.currency)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">
                {t("dashboard.noRecentTransactions")}
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("dashboard.yourLedgers")}</CardTitle>
            <CardDescription>{t("dashboard.financialBooks")}</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : ledgers.length > 0 ? (
              <div className="space-y-3">
                {ledgers.slice(0, 5).map((ledger) => (
                  <div
                    key={ledger.id}
                    className="flex items-center justify-between rounded-lg border p-3"
                  >
                    <div>
                      <p className="font-medium">{ledger.attributes.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {ledger.attributes.currency}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">
                {t("dashboard.noLedgersYet")}
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
