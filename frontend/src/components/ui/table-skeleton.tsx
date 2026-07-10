import { Skeleton } from "@/components/ui/skeleton"

interface TableSkeletonProps {
  rows?: number
}

/** The stacked row skeletons shown while a list/table query is loading. */
export function TableSkeleton({ rows = 3 }: TableSkeletonProps) {
  return (
    <div className="space-y-3">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  )
}
