import { useEffect, useState } from "react"
import { useParams, Link } from "react-router-dom"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { getAccounts, deleteAccount, createAccount } from "@/api/accounts"
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

type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "INCOME" | "EXPENSE"

export function Accounts() {
  const { ledgerSlug } = useParams<{ ledgerSlug?: string }>()
  const { isAuthenticated } = useAuth()
  const [accounts, setAccounts] = useState<AccountResource[]>([])
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [selectedLedgerSlug, setSelectedLedgerSlug] = useState<string | null>(ledgerSlug || null)
  const [isLoading, setIsLoading] = useState(true)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    currency: "USD",
    type: "ASSET" as AccountType,
  })

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
      .catch(console.error)
  }, [isAuthenticated])

  useEffect(() => {
    if (!selectedLedgerSlug || !isAuthenticated) {
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    getAccounts(selectedLedgerSlug)
      .then((response) => {
        setAccounts(response.data)
      })
      .catch(console.error)
      .finally(() => setIsLoading(false))
  }, [selectedLedgerSlug, isAuthenticated])

  const handleDelete = async (accountId: string) => {
    if (!selectedLedgerSlug) return
    if (!confirm("Are you sure you want to delete this account?")) return

    try {
      await deleteAccount(selectedLedgerSlug, accountId)
      setAccounts(accounts.filter((a) => a.id !== accountId))
    } catch (error) {
      console.error("Failed to delete account:", error)
    }
  }

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedLedgerSlug) return
    setIsCreating(true)

    try {
      const response = await createAccount(selectedLedgerSlug, {
        name: formData.name,
        description: formData.description || undefined,
        currency: formData.currency,
        type: formData.type,
      })
      setAccounts([...accounts, response.data])
      setIsCreateDialogOpen(false)
      setFormData({ name: "", description: "", currency: "USD", type: "ASSET" })
    } catch (error) {
      console.error("Failed to create account:", error)
    } finally {
      setIsCreating(false)
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

  const selectedLedger = ledgers.find((l) => l.attributes.slug === selectedLedgerSlug)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Accounts</h2>
          <p className="text-muted-foreground">
            Manage accounts within your ledgers
          </p>
        </div>
        <Button disabled={!selectedLedgerSlug} onClick={() => setIsCreateDialogOpen(true)}>
          <Plus className="mr-2 h-4 w-4" />
          New Account
        </Button>
      </div>

      <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Create New Account</DialogTitle>
            <DialogDescription>
              Add a new account to track your assets, liabilities, income, or expenses.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreate}>
            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="name">Name</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                  placeholder="Checking Account"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="type">Type</Label>
                <Select
                  value={formData.type}
                  onValueChange={(value: AccountType) =>
                    setFormData({ ...formData, type: value })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select account type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ASSET">Asset</SelectItem>
                    <SelectItem value="LIABILITY">Liability</SelectItem>
                    <SelectItem value="EQUITY">Equity</SelectItem>
                    <SelectItem value="INCOME">Income</SelectItem>
                    <SelectItem value="EXPENSE">Expense</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="currency">Currency</Label>
                <Input
                  id="currency"
                  value={formData.currency}
                  onChange={(e) =>
                    setFormData({ ...formData, currency: e.target.value })
                  }
                  placeholder="USD"
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="description">Description (optional)</Label>
                <Input
                  id="description"
                  value={formData.description}
                  onChange={(e) =>
                    setFormData({ ...formData, description: e.target.value })
                  }
                  placeholder="Main checking account for daily expenses"
                />
              </div>
            </div>
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsCreateDialogOpen(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isCreating}>
                {isCreating ? "Creating..." : "Create Account"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

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
          ) : !selectedLedgerSlug ? (
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
              <Button onClick={() => setIsCreateDialogOpen(true)}>
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
