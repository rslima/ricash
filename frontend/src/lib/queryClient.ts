import { QueryClient } from "@tanstack/react-query"

/**
 * Shared TanStack Query client.
 *
 * Server state (data fetched from the API) lives here; local UI state
 * (dialogs, form fields) stays in component `useState`. Mutations invalidate
 * the relevant query keys on success so lists refetch automatically.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Data is considered fresh for 30s before a background refetch is allowed.
      staleTime: 30_000,
      // A 401/403/404/409 won't succeed on retry, so only retry once for
      // transient (e.g. network / 5xx) failures.
      retry: 1,
      refetchOnWindowFocus: true,
    },
  },
})
