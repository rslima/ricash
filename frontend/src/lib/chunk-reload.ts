const RELOADED_AT_KEY = "ricash:chunk-reload-at"

/** A second failure within this window shows the error boundary instead of reloading again. */
export const CHUNK_RELOAD_WINDOW_MS = 60_000

/**
 * After a deploy, an open tab still references the previous build's chunk
 * hashes, so the next lazy route import fails. Vite reports that as
 * `vite:preloadError`; reloading fetches the new index.html and chunks.
 * The sessionStorage timestamp stops a reload loop when the chunk is missing
 * for another reason: the error then propagates to RouteErrorBoundary.
 */
export function installChunkReloadHandler(
  target: Window = window,
  reload: () => void = () => target.location.reload(),
): void {
  target.addEventListener("vite:preloadError", (event) => {
    const last = Number(target.sessionStorage.getItem(RELOADED_AT_KEY) ?? 0)
    if (Date.now() - last < CHUNK_RELOAD_WINDOW_MS) return
    target.sessionStorage.setItem(RELOADED_AT_KEY, String(Date.now()))
    // Suppresses the import error while the page reloads.
    event.preventDefault()
    reload()
  })
}
