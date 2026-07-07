import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react"
import { Button } from "@/components/ui/button"

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  /** Optional left-side content, e.g. a total count and page-size selector. */
  children?: ReactNode
}

/** Zero-based first/prev/next/last pager used below paginated tables and lists. */
export function Pagination({ page, totalPages, onPageChange, children }: PaginationProps) {
  const { t } = useTranslation()

  if (totalPages <= 1) return null

  return (
    <div className="flex items-center justify-between pt-4">
      <div className="flex items-center gap-2 text-sm text-muted-foreground">{children}</div>
      <div className="flex items-center gap-1">
        <span className="text-sm text-muted-foreground mr-2">
          {t("common.pageOf", { page: page + 1, total: totalPages })}
        </span>
        <Button variant="outline" size="icon" className="h-8 w-8" disabled={page === 0} onClick={() => onPageChange(0)}>
          <ChevronsLeft className="h-4 w-4" />
        </Button>
        <Button variant="outline" size="icon" className="h-8 w-8" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <Button variant="outline" size="icon" className="h-8 w-8" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
          <ChevronRight className="h-4 w-4" />
        </Button>
        <Button variant="outline" size="icon" className="h-8 w-8" disabled={page >= totalPages - 1} onClick={() => onPageChange(totalPages - 1)}>
          <ChevronsRight className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
