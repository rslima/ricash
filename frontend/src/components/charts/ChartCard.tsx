import type { ReactNode } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

interface ChartCardProps {
  title: string
  description: string
  isLoading: boolean
  isEmpty: boolean
  emptyLabel: string
  children: ReactNode
}

/** The Card + loading-skeleton + no-data shell shared by the dashboard charts. */
export function ChartCard({ title, description, isLoading, isEmpty, emptyLabel, children }: ChartCardProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <Skeleton className="h-[300px] w-full" />
        ) : isEmpty ? (
          <p className="text-sm text-muted-foreground text-center py-8">{emptyLabel}</p>
        ) : (
          children
        )}
      </CardContent>
    </Card>
  )
}
