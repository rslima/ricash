import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { CHUNK_RELOAD_WINDOW_MS, installChunkReloadHandler } from "./chunk-reload"

// A fresh event target per test, so handlers don't pile up on the real window.
function fakeWindow() {
  return Object.assign(new EventTarget(), { sessionStorage: window.sessionStorage }) as unknown as Window
}

function preloadError(target: Window) {
  const event = new Event("vite:preloadError", { cancelable: true })
  target.dispatchEvent(event)
  return event
}

beforeEach(() => {
  window.sessionStorage.clear()
  vi.useFakeTimers({ toFake: ["Date"] })
  vi.setSystemTime(new Date("2026-03-01T12:00:00Z"))
})

afterEach(() => {
  vi.useRealTimers()
})

describe("installChunkReloadHandler", () => {
  it("reloads once when a lazy chunk fails to load", () => {
    const target = fakeWindow()
    const reload = vi.fn()
    installChunkReloadHandler(target, reload)

    const event = preloadError(target)

    expect(reload).toHaveBeenCalledTimes(1)
    expect(event.defaultPrevented).toBe(true)
  })

  it("lets a repeated failure reach the error boundary instead of looping", () => {
    const target = fakeWindow()
    const reload = vi.fn()
    installChunkReloadHandler(target, reload)
    preloadError(target)

    // The reloaded page fails again (same session storage).
    vi.setSystemTime(Date.now() + 5_000)
    const second = preloadError(target)

    expect(reload).toHaveBeenCalledTimes(1)
    expect(second.defaultPrevented).toBe(false)
  })

  it("reloads again for a later deploy", () => {
    const target = fakeWindow()
    const reload = vi.fn()
    installChunkReloadHandler(target, reload)
    preloadError(target)

    vi.setSystemTime(Date.now() + CHUNK_RELOAD_WINDOW_MS + 1)
    preloadError(target)

    expect(reload).toHaveBeenCalledTimes(2)
  })
})
