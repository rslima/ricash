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
import { getTransactions, deleteTransaction } from "@/api/transactions"
import { getLedgers } from "@/api/ledgers"
import type { TransactionResource, LedgerResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"
import { Plus, Trash2, ArrowLeftRight, MoreHorizontal } from "lucide-react"

export function Transactions() {
  const { ledgerId } = useParams<{ ledgerId?: string }>()
  const { isAuthenticated } = useAuth()
  const [transactions, setTransactions] = useState<TransactionResource[]>([])
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
    getTransactions(selectedLedgerId)
      .then((response) => {
        setTransactions(response.data)
      })
      .catch(console.error)
      .finally(() => setIsLoading(false))
  }, [selectedLedgerId, isAuthenticated])

  const handleDelete = async (transactionId: string) => {
    if (!selectedLedgerId) return
    if (!confirm("Are you sure you want to delete this transaction?")) return

    try {
      await deleteTransaction(selectedLedgerId, transactionId)
      setTransactions(transactions.filter((t) => t.id !== transactionId))
    } catch (error) {
      console.error("Failed to delete transaction:", error)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>Sign in Required</CardTitle>
            <CardDescription>
              Please sign in to view your transactions
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
          <h2 className="text-2xl font-bold tracking-tight">Transactions</h2>
          <p className="text-muted-foreground">
            View and manage your financial transactions
          </p>
        </div>
        <Button disabled={!selectedLedgerId}>
          <Plus className="mr-2 h-4 w-4" />
          New Transaction
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
              ? `Transactions in ${selectedLedger.attributes.name}`
              : "Select a Ledger"}
          </CardTitle>
          <CardDescription>
            Double-entry bookkeeping transactions
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
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">No ledger selected</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Select a ledger above to view its transactions
              </p>
              <Link to="/ledgers">
                <Button variant="outline">Go to Ledgers</Button>
              </Link>
            </div>
          ) : transactions.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Entries</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
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
                            variant={entry.type === "DEBIT" ? "secondary" : "outline"}
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
                    <TableCell>
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem
                            onClick={() => handleDelete(transaction.id)}
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
              <ArrowLeftRight className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">No transactions yet</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Create your first transaction to track your finances
              </p>
              <Button>
                <Plus className="mr-2 h-4 w-4" />
                Create Transaction
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
