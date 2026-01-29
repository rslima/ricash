import { useEffect, useState, useMemo } from "react"
import { useParams, Link } from "react-router-dom"
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
import { useAuth } from "@/contexts/AuthContext"
import { getTransactions } from "@/api/transactions"
import { getAccounts } from "@/api/accounts"
import { getLedgers } from "@/api/ledgers"
import type { TransactionResource, AccountResource, LedgerResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { ArrowLeft, ArrowLeftRight, ChevronRight } from "lucide-react"

// Build breadcrumb path for an account
function buildAccountBreadcrumb(
  accountId: string,
  accounts: AccountResource[]
): string[] {
  const accountMap = new Map<string, AccountResource>()
  accounts.forEach((account) => {
    accountMap.set(account.id, account)
  })

  const breadcrumb: string[] = []
  let currentId: string | undefined = accountId

  while (currentId && accountMap.has(currentId)) {
    const account = accountMap.get(currentId)!
    breadcrumb.unshift(account.attributes.name)
    currentId = account.attributes.parentAccountId || undefined
  }

  return breadcrumb
}

export function AccountTransactions() {
  const { ledgerSlug, accountId } = useParams<{ ledgerSlug: string; accountId: string }>()
  const { isAuthenticated } = useAuth()
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [ledger, setLedger] = useState<LedgerResource | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const account = useMemo(
    () => accounts.find((a) => a.id === accountId),
    [accounts, accountId]
  )

  const breadcrumb = useMemo(
    () => (accountId ? buildAccountBreadcrumb(accountId, accounts) : []),
    [accountId, accounts]
  )

  useEffect(() => {
    if (!isAuthenticated || !ledgerSlug || !accountId) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    Promise.all([
      getTransactions(ledgerSlug, { accountId }),
      getAccounts(ledgerSlug),
      getLedgers(),
    ])
      .then(([transactionsResponse, accountsResponse, ledgersResponse]) => {
        setTransactions(transactionsResponse.data)
        setAccounts(accountsResponse.data)
        const foundLedger = ledgersResponse.data.find(
          (l) => l.attributes.slug === ledgerSlug
        )
        setLedger(foundLedger || null)
      })
      .catch(console.error)
      .finally(() => setIsLoading(false))
  }, [isAuthenticated, ledgerSlug, accountId])

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>Sign in Required</CardTitle>
            <CardDescription>
              Please sign in to view account transactions
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <div className="space-y-6">
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
            {account ? `Transactions for ${account.attributes.name}` : "Account Transactions"}
          </h2>
          <p className="text-muted-foreground">
            {ledger && `${ledger.attributes.name} - `}
            Showing all transactions involving this account
          </p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Transactions</CardTitle>
          <CardDescription>
            {transactions.length} transaction{transactions.length !== 1 ? "s" : ""} found
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
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Entries</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
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
                            {entry.accountName}: {entry.type === "DEBIT" ? "DR" : "CR"}
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
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">No transactions yet</h3>
              <p className="text-sm text-muted-foreground mb-4">
                This account has no transactions
              </p>
              <Link to={`/ledgers/${ledgerSlug}/transactions`}>
                <Button variant="outline">Go to Transactions</Button>
              </Link>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
