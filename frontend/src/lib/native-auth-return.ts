/**
 * Hand-off for the post-login destination on native platforms.
 *
 * The deep-link listener that finishes a native sign-in runs inside
 * AuthProvider, which wraps BrowserRouter — so it sits outside the router and
 * cannot navigate. It publishes the resolved path here, and NativeAuthReturn
 * (rendered inside the router) picks it up and navigates.
 *
 * A plain module-level store rather than context: the publisher and the
 * consumer live on opposite sides of the router boundary, which is exactly
 * what a context cannot span here.
 */

let pendingReturnTo: string | null = null
const subscribers = new Set<() => void>()

/** Publishes the path to navigate to once the router-side consumer sees it. */
export function publishReturnTo(path: string): void {
  pendingReturnTo = path
  subscribers.forEach((notify) => notify())
}

/** Clears the pending path; call after navigating so it fires only once. */
export function consumeReturnTo(): void {
  pendingReturnTo = null
  subscribers.forEach((notify) => notify())
}

export function subscribeReturnTo(notify: () => void): () => void {
  subscribers.add(notify)
  return () => {
    subscribers.delete(notify)
  }
}

export function getPendingReturnTo(): string | null {
  return pendingReturnTo
}
