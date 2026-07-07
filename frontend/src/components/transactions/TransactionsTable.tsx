import { useTranslation } from "react-i18next"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Badge } from "@/components/ui/badge"
import { TransactionRowActions } from "@/components/transactions/TransactionRowActions"
import type { TransactionResource } from "@/api/types"
import { formatCurrency, formatDate } from "@/lib/utils"

interface TransactionsTableProps {
  transactions: TransactionResource[]
  onEdit: (transaction: TransactionResource) => void
  onDelete: (transactionId: string) => void
}

/** Desktop table presentation of the transaction list. */
export function TransactionsTable({ transactions, onEdit, onDelete }: TransactionsTableProps) {
  const { t } = useTranslation()

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t("common.date")}</TableHead>
          <TableHead>{t("common.description")}</TableHead>
          <TableHead>{t("transactions.entries")}</TableHead>
          <TableHead className="text-right">{t("common.amount")}</TableHead>
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
                  <Badge key={idx} variant={entry.type === "DEBIT" ? "secondary" : "outline"}>
                    {entry.accountName}: {entry.type === "DEBIT" ? "DB" : "CR"}
                    {entry.instrumentSymbol && ` (${entry.instrumentSymbol})`}
                    {entry.quantity && ` x${entry.quantity}`}
                  </Badge>
                ))}
              </div>
            </TableCell>
            <TableCell className="text-right font-mono">
              {formatCurrency(transaction.attributes.amount, transaction.attributes.currency)}
            </TableCell>
            <TableCell>
              <TransactionRowActions transaction={transaction} onEdit={onEdit} onDelete={onDelete} />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
