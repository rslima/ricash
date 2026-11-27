/** Name of the Workbox runtime cache older builds stored API responses in. */
export const LEGACY_API_CACHE = "api-cache"

/**
 * Deletes the API response cache that service workers from older builds
 * wrote. The current service worker no longer creates it, but removing the
 * route does not delete a cache already on the device. Best effort: missing
 * Cache Storage (insecure origin, old WebView) or a failure is ignored.
 */
export async function purgeLegacyApiCache(): Promise<void> {
  if (typeof caches === "undefined") return
  try {
    await caches.delete(LEGACY_API_CACHE)
  } catch {
    // Nothing to do: the next startup tries again.
  }
}
