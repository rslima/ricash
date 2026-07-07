import { useTranslation } from "react-i18next"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { TableCell, TableRow } from "@/components/ui/table"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import type { EnvelopeResource } from "@/api/types"
import { ChevronDown, ChevronRight, FolderOpen, Link as LinkIcon, MoreHorizontal, Pencil, Plus, Trash2 } from "lucide-react"

export const envelopeTypeColors: Record<string, "default" | "secondary" | "destructive" | "outline"> = {
  EXPENSE: "destructive",
  INCOME: "default",
}

export interface EnvelopeTreeNode {
  envelope: EnvelopeResource
  children: EnvelopeTreeNode[]
}

export function buildEnvelopeTree(envelopes: EnvelopeResource[]): EnvelopeTreeNode[] {
  const envelopeMap = new Map<string, EnvelopeTreeNode>()
  const roots: EnvelopeTreeNode[] = []

  envelopes.forEach((envelope) => {
    envelopeMap.set(envelope.id, { envelope, children: [] })
  })

  envelopes.forEach((envelope) => {
    const node = envelopeMap.get(envelope.id)!
    const parentId = envelope.attributes.parentEnvelopeId

    if (parentId && envelopeMap.has(parentId)) {
      envelopeMap.get(parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  })

  return roots
}

export function countTreeNodes(nodes: EnvelopeTreeNode[]): number {
  let count = 0
  for (const node of nodes) {
    count += 1 + countTreeNodes(node.children)
  }
  return count
}

interface EnvelopeRowProps {
  node: EnvelopeTreeNode
  depth: number
  expandedIds: Set<string>
  onToggleExpand: (id: string) => void
  onEdit: (envelope: EnvelopeResource) => void
  onDelete: (envelopeId: string) => void
  onCreateChild: (envelope: EnvelopeResource) => void
  onManageAccounts: (envelope: EnvelopeResource) => void
}

/** One envelope table row plus, when expanded, its subtree. */
export function EnvelopeRow({
  node,
  depth,
  expandedIds,
  onToggleExpand,
  onEdit,
  onDelete,
  onCreateChild,
  onManageAccounts,
}: EnvelopeRowProps) {
  const { t } = useTranslation()
  const { envelope, children } = node
  const hasChildren = children.length > 0
  const isExpanded = expandedIds.has(envelope.id)
  const isArchived = envelope.attributes.status === "ARCHIVED"

  return (
    <>
      <TableRow className={isArchived ? "opacity-50" : ""}>
        <TableCell className="font-medium">
          <div className="flex items-center gap-2" style={{ paddingLeft: `${depth * 24}px` }}>
            {hasChildren ? (
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6 p-0"
                onClick={() => onToggleExpand(envelope.id)}
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
            <FolderOpen className="h-4 w-4 text-muted-foreground" />
            <span>{envelope.attributes.name}</span>
            {isArchived && (
              <Badge variant="outline" className="ml-2">
                {t("envelopes.archived")}
              </Badge>
            )}
          </div>
        </TableCell>
        <TableCell>{envelope.attributes.currency}</TableCell>
        <TableCell>
          <Badge variant={envelopeTypeColors[envelope.attributes.type]}>
            {t(`envelopes.types.${envelope.attributes.type}`)}
          </Badge>
        </TableCell>
        <TableCell>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon">
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => onCreateChild(envelope)}>
                <Plus className="mr-2 h-4 w-4" />
                {t("envelopes.createChildEnvelope")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onManageAccounts(envelope)}>
                <LinkIcon className="mr-2 h-4 w-4" />
                {t("envelopes.manageAccounts")}
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => onEdit(envelope)}>
                <Pencil className="mr-2 h-4 w-4" />
                {t("common.edit")}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => onDelete(envelope.id)}
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
          <EnvelopeRow
            key={child.envelope.id}
            node={child}
            depth={depth + 1}
            expandedIds={expandedIds}
            onToggleExpand={onToggleExpand}
            onEdit={onEdit}
            onDelete={onDelete}
            onCreateChild={onCreateChild}
            onManageAccounts={onManageAccounts}
          />
        ))}
    </>
  )
}
