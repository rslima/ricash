import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query"
import { ApiError } from "@/api/client"
import { isUnauthorized, recoverFromUnauthorized } from "@/lib/session-recovery"

function onError(error: unknown) {
  if (isUnauthorized(error)) {
    void recoverFromUnauthorized(queryClient)
  }
}

/**
 * Shared TanStack Query client.
 *
 * Server state (data fetched from the API) lives here; local UI state
 * (dialogs, form fields) stays in component `useState`. Mutations invalidate
 * the relevant query keys on success so lists refetch automatically.
 *
 * Any 401, from a query or a mutation, goes to lib/session-recovery: one
 * silent renew, else back to the sign-in screen.
 */
export const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
  defaultOptions: {
    queries: {
      // Data is considered fresh for 30s before a background refetch is allowed.
      staleTime: 30_000,
      // A 401/403/404/409 won't succeed on retry, so only retry once for
      // transient (e.g. network / 5xx) failures.
      retry: (failureCount, error) =>
        failureCount < 1 && (!(error instanceof ApiError) || error.status >= 500),
      refetchOnWindowFocus: true,
    },
  },
})
