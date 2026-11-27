import type { QueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import i18n from "@/i18n"
import { apiClient, ApiError } from "@/api/client"
import { userManager } from "@/lib/oidc"

/**
 * A 401 arriving this soon after a successful renew counts as a failure, so
 * an API that keeps rejecting fresh tokens signs the user out instead of
 * renewing in a loop.
 */
export const RENEW_LOOP_WINDOW_MS = 30_000

let inFlight: Promise<void> | null = null
let lastRenewAt = Number.NEGATIVE_INFINITY

export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

/**
 * Recovers from a 401: one silent renew (the refresh-token grant when a
 * refresh token exists, else the silent-renew iframe) and a refetch with the
 * new token; when that fails, a local sign-out so pages fall back to their
 * sign-in card instead of failing every request. Single-flight: concurrent
 * 401s share one attempt.
 *
 * Without this, react-oidc-context keeps isAuthenticated true after the token
 * expires (it only re-evaluates when a user is loaded), so a session whose
 * renewal failed stays "signed in" while every call returns 401.
 */
export function recoverFromUnauthorized(queryClient: QueryClient): Promise<void> {
  inFlight ??= recover(queryClient).finally(() => {
    inFlight = null
  })
  return inFlight
}

async function recover(queryClient: QueryClient): Promise<void> {
  // Never signed in (or already signed out): nothing to recover.
  if (!(await userManager.getUser())) return

  if (Date.now() - lastRenewAt >= RENEW_LOOP_WINDOW_MS) {
    try {
      const user = await userManager.signinSilent()
      if (user?.access_token) {
        lastRenewAt = Date.now()
        // Set the token before refetching: AuthProvider only syncs it on its
        // next render, which would come after these refetches start.
        apiClient.setAccessToken(user.access_token)
        // Not awaited, so a refetch that 401s again starts a new attempt,
        // which the loop window above turns into a sign-out.
        void queryClient.invalidateQueries()
        return
      }
    } catch (error) {
      console.warn("Silent renew after a 401 failed:", error)
    }
  }

  // USER_UNLOADED flips react-oidc-context's isAuthenticated to false, so
  // pages render SignInRequired. Clearing the cache keeps the previous
  // user's data from showing to whoever signs in next.
  await userManager.removeUser()
  apiClient.setAccessToken(null)
  queryClient.clear()
  toast.error(i18n.t("errors.unauthorized"), { id: "session-expired" })
}

/** Test hook: forget the in-flight attempt and the last renew time. */
export function resetSessionRecoveryForTests(): void {
  inFlight = null
  lastRenewAt = Number.NEGATIVE_INFINITY
}
