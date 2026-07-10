import { useCallback, useState } from "react"

/**
 * Page/page-size state for `page[number]`/`page[size]` paginated lists.
 * Changing the page size restarts from the first page; `resetPage` is for
 * filter/ledger changes that must do the same. The callbacks are stable so
 * they can appear in effect dependency lists.
 */
export function usePagination(initialPageSize = 20) {
  const [currentPage, setCurrentPage] = useState(0)
  const [pageSize, setPageSizeState] = useState(initialPageSize)

  const setPageSize = useCallback((size: number) => {
    setPageSizeState(size)
    setCurrentPage(0)
  }, [])
  const resetPage = useCallback(() => setCurrentPage(0), [])

  return { currentPage, setCurrentPage, pageSize, setPageSize, resetPage }
}
