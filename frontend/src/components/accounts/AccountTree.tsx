import { Link } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { TableCell, TableRow } from "@/components/ui/table"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { AccountResource } from "@/api/types"
import { formatCurrency } from "@/lib/utils"
import { ChevronDown, ChevronRight, MoreHorizontal, Pencil, Plus, Trash2, Wallet } from "lucide-react"

export interface AccountTreeNode {
  account: AccountResource
  children: AccountTreeNode[]
}

export function buildAccountTree(accounts: AccountResource[]): AccountTreeNode[] {
  const accountMap = new Map<string, AccountTreeNode>()
  const roots: AccountTreeNode[] = []

  // Create nodes for all accounts
  accounts.forEach((account) => {
    accountMap.set(account.id, { account, children: [] })
  })

  // Build the tree structure
  accounts.forEach((account) => {
    const node = accountMap.get(account.id)!
    const parentId = account.attributes.parentAccountId

    if (parentId && accountMap.has(parentId)) {
      accountMap.get(parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

export function countTreeNodes(nodes: AccountTreeNode[]): number {
  let count = 0
  for (const node of nodes) {
    count += 1 + countTreeNodes(node.children)
  }
  return count
}

interface AccountRowProps {
  node: AccountTreeNode
  depth: number
  expandedIds: Set<string>
  onToggleExpand: (id: string) => void
  onEdit: (account: AccountResource) => void
  onDelete: (accountId: string) => void
  onCreateChild: (account: AccountResource) => void
  ledgerSlug: string
}

/** One account table row plus, when expanded, its subtree. */
export function AccountRow({ node, depth, expandedIds, onToggleExpand, onEdit, onDelete, onCreateChild, ledgerSlug }: AccountRowProps) {
  const { t } = useTranslation()
  const { account, children } = node
  const hasChildren = children.length > 0
  const isExpanded = expandedIds.has(account.id)

  return (
    <>
      <TableRow>
        <TableCell className="font-medium">
          <div className="flex items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
            {hasChildren ? (
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6 p-0"
                onClick={() => onToggleExpand(account.id)}
              >
                {isExpanded ? (
                  <ChevronDown className="h-4 w-4" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )}
              </Button>
            ) : (
              <span className="w-6" />
            )}
            <Wallet className="h-4 w-4 text-muted-foreground" />
            <Link
              to={`/ledgers/${ledgerSlug}/accounts/${account.id}/transactions`}
              className="hover:underline hover:text-primary"
            >
              {account.attributes.name}
            </Link>
          </div>
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
              <DropdownMenuItem onClick={() => onCreateChild(account)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("accounts.createChildAccount")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onEdit(account)}>
                <Pencil className="mr-2 h-4 w-4" />
                {t("common.edit")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onDelete(account.id)}
                className="text-destructive"
              >
                <Trash2 className="mr-2 h-4 w-4" />
                {t("common.delete")}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </TableCell>
      </TableRow>
      {isExpanded &&
        children.map((child) => (
          <AccountRow
            key={child.account.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggleExpand={onToggleExpand}
            onEdit={onEdit}
            onDelete={onDelete}
            onCreateChild={onCreateChild}
            ledgerSlug={ledgerSlug}
          />
        ))}
    </>
  )
}
