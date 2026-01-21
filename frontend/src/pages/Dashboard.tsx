import { useEffect, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"
import { getLedgers } from "@/api/ledgers"
import type { LedgerResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { Wallet, ArrowUpRight, ArrowDownRight, BookOpen } from "lucide-react"

export function Dashboard() {
  const { isAuthenticated, user } = useAuth()
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (isAuthenticated) {
      getLedgers()
        .then((response) => {
          setLedgers(response.data)
        })
        .catch(console.error)
        .finally(() => setIsLoading(false))
    } else {
      setIsLoading(false)
    }
  }, [isAuthenticated])

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>Welcome to Ricash</CardTitle>
            <CardDescription>
              Please sign in to manage your personal finances
            </CardDescription>
          </CardHeader>
          <CardContent className="text-center">
            <p className="text-muted-foreground">
              Track your income, expenses, and manage multiple ledgers with double-entry bookkeeping.
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
          Welcome back, {user?.name}
        </h2>
        <p className="text-muted-foreground">
          Here's an overview of your finances
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total Ledgers</CardTitle>
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
            <CardTitle className="text-sm font-medium">Total Balance</CardTitle>
            <Wallet className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="text-2xl font-bold">{formatCurrency(0)}</div>
            )}
            <p className="text-xs text-muted-foreground">Across all accounts</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Income</CardTitle>
            <ArrowUpRight className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="text-2xl font-bold text-green-600">
                {formatCurrency(0)}
              </div>
            )}
            <p className="text-xs text-muted-foreground">This month</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Expenses</CardTitle>
            <ArrowDownRight className="h-4 w-4 text-red-500" />
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <Skeleton className="h-8 w-24" />
            ) : (
              <div className="text-2xl font-bold text-red-600">
                {formatCurrency(0)}
              </div>
            )}
            <p className="text-xs text-muted-foreground">This month</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Recent Transactions</CardTitle>
            <CardDescription>Your latest financial activities</CardDescription>
          </CardHeader>
          <CardContent>
            {isLoading ? (
              <div className="space-y-3">
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
                <Skeleton className="h-12 w-full" />
              </div>
            ) : (
              <p className="text-sm text-muted-foreground text-center py-8">
                No recent transactions
              </p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Your Ledgers</CardTitle>
            <CardDescription>Financial books you manage</CardDescription>
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
                No ledgers yet. Create your first ledger to get started.
              </p>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
