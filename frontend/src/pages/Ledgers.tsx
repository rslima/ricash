import { useEffect, useState } from "react"
import { Link } from "react-router-dom"
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
import { getLedgers, deleteLedger } from "@/api/ledgers"
import type { LedgerResource } from "@/api/types"
import { formatDate } from "@/lib/utils"
import { Plus, Trash2, BookOpen, MoreHorizontal } from "lucide-react"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

export function Ledgers() {
  const { isAuthenticated } = useAuth()
  const [ledgers, setLedgers] = useState<LedgerResource[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const fetchLedgers = async () => {
    if (!isAuthenticated) {
      setIsLoading(false)
      return
    }

    try {
      const response = await getLedgers()
      setLedgers(response.data)
    } catch (error) {
      console.error("Failed to fetch ledgers:", error)
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    fetchLedgers()
  }, [isAuthenticated])

  const handleDelete = async (id: string) => {
    if (!confirm("Are you sure you want to delete this ledger?")) return

    try {
      await deleteLedger(id)
      setLedgers(ledgers.filter((l) => l.id !== id))
    } catch (error) {
      console.error("Failed to delete ledger:", error)
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>Sign in Required</CardTitle>
            <CardDescription>
              Please sign in to view your ledgers
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
          <h2 className="text-2xl font-bold tracking-tight">Ledgers</h2>
          <p className="text-muted-foreground">
            Manage your financial ledgers
          </p>
        </div>
        <Button>
          <Plus className="mr-2 h-4 w-4" />
          New Ledger
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>All Ledgers</CardTitle>
          <CardDescription>
            A ledger is a collection of accounts for tracking finances
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : ledgers.length > 0 ? (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Currency</TableHead>
                  <TableHead>Description</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead className="w-[70px]"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {ledgers.map((ledger) => (
                  <TableRow key={ledger.id}>
                    <TableCell>
                      <Link
                        to={`/ledgers/${ledger.id}`}
                        className="font-medium hover:underline flex items-center gap-2"
                      >
                        <BookOpen className="h-4 w-4 text-muted-foreground" />
                        {ledger.attributes.name}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {ledger.attributes.currency}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {ledger.attributes.description || "—"}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(ledger.attributes.createdAt)}
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
                            onClick={() => handleDelete(ledger.id)}
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
              <BookOpen className="h-12 w-12 text-muted-foreground mb-4" />
              <h3 className="text-lg font-semibold">No ledgers yet</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Create your first ledger to start tracking your finances
              </p>
              <Button>
                <Plus className="mr-2 h-4 w-4" />
                Create Ledger
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
