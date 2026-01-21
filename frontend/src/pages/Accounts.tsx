import { useEffect, useState } from "react"
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useAuth } from "@/contexts/AuthContext"
import { getAccounts, deleteAccount } from "@/api/accounts"
import { getLedgers } from "@/api/ledgers"
import type { AccountResource, LedgerResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { Plus, Trash2, Wallet, MoreHorizontal } from "lucide-react"

const accountTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  ASSET: "default",
  LIABILITY: "destructive",
  EQUITY: "secondary",
  INCOME: "default",
  EXPENSE: "destructive",
}

export function Accounts() {
  const { ledgerId } = useParams<{ ledgerId?: string }>()
  const { isAuthenticated } = useAuth()
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerId, setSelectedLedgerId] = useState<string | null>(ledgerId || null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    getLedgers()
      .then((response) => {
        setLedgers(response.data)
        if (!selectedLedgerId && response.data.length > 0) {
          setSelectedLedgerId(response.data[0].id)
        }
      })
      .catch(console.error)
  }, [isAuthenticated])

  useEffect(() => {
    if (!selectedLedgerId || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    getAccounts(selectedLedgerId)
      .then((response) => {
        setAccounts(response.data)
      })
      .catch(console.error)
      .finally(() => setIsLoading(false))
  }, [selectedLedgerId, isAuthenticated])

  const handleDelete = async (accountId: string) => {
    if (!selectedLedgerId) return
    if (!confirm("Are you sure you want to delete this account?")) return

    try {
      await deleteAccount(selectedLedgerId, accountId)
      setAccounts(accounts.filter((a) => a.id !== accountId))
    } catch (error) {
      console.error("Failed to delete account:", error)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>Sign in Required</CardTitle>
            <CardDescription>
              Please sign in to view your accounts
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  const selectedLedger = ledgers.find((l) => l.id === selectedLedgerId)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Accounts</h2>
          <p className="text-muted-foreground">
            Manage accounts within your ledgers
          </p>
        </div>
        <Button disabled={!selectedLedgerId}>
          <Plus className="mr-2 h-4 w-4" />
          New Account
        </Button>
      </div>

      {ledgers.length > 0 && (
        <div className="flex gap-2 flex-wrap">
          {ledgers.map((ledger) => (
            <Button
              key={ledger.id}
              variant={selectedLedgerId === ledger.id ? "default" : "outline"}
              size="sm"
              onClick={() => setSelectedLedgerId(ledger.id)}
            >
              {ledger.attributes.name}
            </Button>
          ))}
        </div>
      )}

      <Card>
        <CardHeader>
          <CardTitle>
            {selectedLedger
              ? `Accounts in ${selectedLedger.attributes.name}`
              : "Select a Ledger"}
          </CardTitle>
          <CardDescription>
            Track your assets, liabilities, income, and expenses
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : !selectedLedgerId ? (
            <div className="flex flex-col items-center justify-center py-12">
              <Wallet className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">No ledger selected</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Select a ledger above to view its accounts
              </p>
              <Link to="/ledgers">
                <Button variant="outline">Go to Ledgers</Button>
              </Link>
            </div>
          ) : accounts.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Currency</TableHead>
                  <TableHead className="text-right">Balance</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {accounts.map((account) => (
                  <TableRow key={account.id}>
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <Wallet className="h-4 w-4 text-muted-foreground" />
                        {account.attributes.name}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={accountTypeColors[account.attributes.type]}>
                        {account.attributes.type}
                      </Badge>
                    </TableCell>
                    <TableCell>{account.attributes.currency}</TableCell>
                    <TableCell className="text-right font-mono">
                      {formatCurrency(account.attributes.balance, account.attributes.currency)}
                    </TableCell>
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem
                            onClick={() => handleDelete(account.id)}
                            className="text-destructive"
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            Delete
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <div className="flex flex-col items-center justify-center py-12">
              <Wallet className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">No accounts yet</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Create your first account to start tracking
              </p>
              <Button>
                <Plus className="mr-2 h-4 w-4" />
                Create Account
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
