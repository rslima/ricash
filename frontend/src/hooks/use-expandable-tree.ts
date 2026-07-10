import { useEffect, useState } from "react"

/**
 * Expand/collapse state for tree tables. Expands everything whenever the
 * item list (re)loads — pass a stable (memoized/query-owned) array or the
 * default-expand effect will retrigger every render.
 */
export function useExpandableTree(items: ReadonlyArray<{ id: string }>) {
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set())

  useEffect(() => {
    setExpandedIds(new Set(items.map((item) => item.id)))
  }, [items])

  const toggle = (id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) {
        next.delete(id)
      } else {
        next.add(id)
      }
      return next
    })
  }

  const expandAll = () => setExpandedIds(new Set(items.map((item) => item.id)))
  const collapseAll = () => setExpandedIds(new Set())

  return { expandedIds, toggle, expandAll, collapseAll }
}
