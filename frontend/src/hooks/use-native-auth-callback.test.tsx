import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { renderHook, waitFor, act } from "@testing-library/react"
import { userManager } from "@/lib/oidc"
import { consumeReturnTo, getPendingReturnTo } from "@/lib/native-auth-return"
import { useNativeAuthCallback } from "./use-native-auth-callback"

const NATIVE_REDIRECT_URI = "com.ricash.app://callback"

vi.mock("@/lib/capacitor", () => ({ isNativePlatform: () => true }))

vi.mock("@/lib/oidc", () => ({
  NATIVE_REDIRECT_URI: "com.ricash.app://callback",
  userManager: { signinRedirectCallback: vi.fn() },
}))

// Captures the appUrlOpen handler so tests can fire the deep link themselves.
let deepLinkHandler: ((event: { url: string }) => Promise<void>) | undefined
const removeListener = vi.fn()

vi.mock("@capacitor/app", () => ({
  App: {
    addListener: vi.fn(async (_event: string, handler: (e: { url: string }) => Promise<void>) => {
      deepLinkHandler = handler
      return { remove: removeListener }
    }),
  },
}))

vi.mock("@capacitor/browser", () => ({ Browser: { close: vi.fn() } }))

/** Mounts the hook and waits for the deep-link listener to register. */
async function mountListener(onError = vi.fn()) {
  const result = renderHook(() => useNativeAuthCallback(onError))
  await waitFor(() => expect(deepLinkHandler).toBeDefined())
  return { onError, ...result }
}

beforeEach(() => {
  deepLinkHandler = undefined
  vi.mocked(userManager.signinRedirectCallback).mockReset()
})

afterEach(() => {
  consumeReturnTo()
})

describe("useNativeAuthCallback", () => {
  it("publishes the return path carried by the signin state", async () => {
    vi.mocked(userManager.signinRedirectCallback).mockResolvedValue({
      state: { returnTo: "/ledgers/home/accounts?page=2" },
    } as never)

    await mountListener()
    await act(async () => {
      await deepLinkHandler!({ url: `${NATIVE_REDIRECT_URI}?code=abc&state=xyz` })
    })

    expect(getPendingReturnTo()).toBe("/ledgers/home/accounts?page=2")
  })

  it("falls back to home when the signin carried no state", async () => {
    vi.mocked(userManager.signinRedirectCallback).mockResolvedValue({ state: undefined } as never)

    await mountListener()
    await act(async () => {
      await deepLinkHandler!({ url: `${NATIVE_REDIRECT_URI}?code=abc` })
    })

    expect(getPendingReturnTo()).toBe("/")
  })

  it("rejects an off-origin return path rather than navigating off-site", async () => {
    vi.mocked(userManager.signinRedirectCallback).mockResolvedValue({
      state: { returnTo: "https://evil.example/steal" },
    } as never)

    await mountListener()
    await act(async () => {
      await deepLinkHandler!({ url: `${NATIVE_REDIRECT_URI}?code=abc` })
    })

    expect(getPendingReturnTo()).toBe("/")
  })

  it("ignores deep links that are not the OIDC redirect", async () => {
    await mountListener()
    await act(async () => {
      await deepLinkHandler!({ url: "com.ricash.app://something-else" })
    })

    expect(userManager.signinRedirectCallback).not.toHaveBeenCalled()
    expect(getPendingReturnTo()).toBeNull()
  })

  it("reports an error and publishes nothing when the callback fails", async () => {
    vi.mocked(userManager.signinRedirectCallback).mockRejectedValue(new Error("bad state"))

    const { onError } = await mountListener()
    await act(async () => {
      await deepLinkHandler!({ url: `${NATIVE_REDIRECT_URI}?code=abc` })
    })

    expect(onError).toHaveBeenCalledWith("bad state")
    expect(getPendingReturnTo()).toBeNull()
  })
})
