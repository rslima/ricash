import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { TransactionResource } from "@/api/types"
import { MoreHorizontal, Pencil, Trash2 } from "lucide-react"

interface TransactionRowActionsProps {
  transaction: TransactionResource
  onEdit: (transaction: TransactionResource) => void
  onDelete: (transactionId: string) => void
  compact?: boolean
}

/** Edit/delete dropdown shared by the table and card presentations. */
export function TransactionRowActions({ transaction, onEdit, onDelete, compact }: TransactionRowActionsProps) {
  const { t } = useTranslation()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" className={compact ? "h-8 w-8" : undefined}>
          <MoreHorizontal className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => onEdit(transaction)}>
          <Pencil className="mr-2 h-4 w-4" />
          {t("common.edit")}
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => onDelete(transaction.id)} className="text-destructive">
          <Trash2 className="mr-2 h-4 w-4" />
          {t("common.delete")}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
