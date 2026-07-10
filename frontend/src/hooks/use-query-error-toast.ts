import { useEffect } from "react"
import { useErrorHandler } from "@/hooks/use-error-handler"

/** The slice of a TanStack Query result the aggregation helpers need. */
export interface QueryStateLike {
  isLoading: boolean
  isError: boolean
  error: unknown
}

/** Folds several query results into one aggregate loading/error signal. */
export function combineQueries(...queries: QueryStateLike[]): QueryStateLike {
  return {
    isLoading: queries.some((q) => q.isLoading),
    isError: queries.some((q) => q.isError),
    error: queries.find((q) => q.isError)?.error ?? null,
  }
}

/**
 * Surfaces fetch failures from the given queries as the shared error toast
 * (mutations keep reporting their own errors inline).
 */
export function useQueryErrorToast(queries: QueryStateLike | QueryStateLike[], operationKey = "fetchFailed") {
  const handleError = useErrorHandler()
  const list = Array.isArray(queries) ? queries : [queries]
  const isError = list.some((q) => q.isError)
  const error = list.find((q) => q.isError)?.error
  useEffect(() => {
    if (isError) handleError(error, operationKey)
  }, [isError, error, handleError, operationKey])
}
