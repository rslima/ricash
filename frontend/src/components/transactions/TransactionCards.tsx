import { Badge } from "@/components/ui/badge"
import { TransactionRowActions } from "@/components/transactions/TransactionRowActions"
import type { TransactionResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"

interface TransactionCardsProps {
  transactions: TransactionResource[]
  onEdit: (transaction: TransactionResource) => void
  onDelete: (transactionId: string) => void
}

/** Mobile card presentation of the transaction list. */
export function TransactionCards({ transactions, onEdit, onDelete }: TransactionCardsProps) {
  return (
    <div className="space-y-2">
      {transactions.map((transaction) => (
        <div key={transaction.id} className="rounded-lg border bg-card p-3">
          <div className="flex items-start justify-between">
            <div className="min-w-0 flex-1">
              <p className="font-medium truncate">{transaction.attributes.description}</p>
              <p className="text-xs text-muted-foreground mt-0.5">
                {formatDate(transaction.attributes.date)}
              </p>
            </div>
            <div className="flex items-center gap-1 ml-2 shrink-0">
              <span className="font-mono text-sm font-medium">
                {formatCurrency(transaction.attributes.amount, transaction.attributes.currency)}
              </span>
              <TransactionRowActions
                transaction={transaction}
                onEdit={onEdit}
                onDelete={onDelete}
                compact
              />
            </div>
          </div>
          <div className="flex flex-wrap gap-1 mt-2">
            {transaction.attributes.entries?.map((entry, idx) => (
              <Badge
                key={idx}
                variant={entry.type === "DEBIT" ? "secondary" : "outline"}
                className="text-[10px]"
              >
                {entry.accountName}: {entry.type === "DEBIT" ? "DB" : "CR"}
                {entry.instrumentSymbol && ` (${entry.instrumentSymbol})`}
                {entry.quantity && ` x${entry.quantity}`}
              </Badge>
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}
