import { QueryClient } from "@tanstack/react-query"

// Auth-scoped in-memory cache: cleared on logout (see AuthContext). This
// replaces the removed service-worker API cache with semantics that never
// persist authenticated data.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: 1,
    },
  },
})
