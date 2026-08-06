/**
 * Carries the pre-login location across the OIDC redirect round-trip.
 *
 * The sign-in button lives in the app shell, so the browser leaves for the
 * identity provider from whatever page the user was on and comes back at
 * /callback. Without this the callback has no idea where to send them.
 *
 * The path travels as the OIDC `state` (custom state data, echoed back on
 * `User.state`) rather than in storage, so it survives the full-page
 * navigation and stays scoped to the one sign-in attempt that set it.
 */

/** Shape of the custom state attached to a signin request. */
export interface AuthReturnState {
  returnTo: string
}

/** The current location, as an app-relative path, for restoring after login. */
export function captureReturnTo(): string {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`
}

/**
 * Reads the return path back out of the OIDC user state, falling back to home.
 *
 * The value round-trips through the identity provider, so it is treated as
 * untrusted: anything resolving off-origin (an absolute URL, or a
 * protocol-relative //host path) would turn login into an open redirect, and
 * /callback itself would bounce the user straight back here.
 */
export function resolveReturnTo(state: unknown): string {
  const returnTo = (state as Partial<AuthReturnState> | null | undefined)?.returnTo
  if (typeof returnTo !== "string" || !returnTo.startsWith("/")) return "/"

  let url: URL
  try {
    url = new URL(returnTo, window.location.origin)
  } catch {
    return "/"
  }

  if (url.origin !== window.location.origin) return "/"
  if (url.pathname === "/callback") return "/"

  return `${url.pathname}${url.search}${url.hash}`
}
