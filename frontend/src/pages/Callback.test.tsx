import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { waitFor } from "@testing-library/react"
import { useAuth as useOidcAuth } from "react-oidc-context"
import { renderWithProviders } from "@/test/test-utils"
import { Callback } from "./Callback"

vi.mock("react-oidc-context", () => ({ useAuth: vi.fn() }))

/** Minimal stand-in for the react-oidc-context value Callback reads. */
function mockOidcAuth(overrides: Record<string, unknown> = {}) {
  vi.mocked(useOidcAuth).mockReturnValue({
    isAuthenticated: false,
    error: undefined,
    user: undefined,
    ...overrides,
  } as unknown as ReturnType<typeof useOidcAuth>)
}

beforeEach(() => {
  window.history.pushState({}, "", "/callback")
})

afterEach(() => {
  window.history.pushState({}, "", "/")
})

describe("Callback", () => {
  it("returns the user to the page they signed in from", async () => {
    mockOidcAuth({ isAuthenticated: true, user: { state: { returnTo: "/transactions?page=2" } } })

    renderWithProviders(<Callback />)

    await waitFor(() => {
      expect(window.location.pathname + window.location.search).toBe("/transactions?page=2")
    })
  })

  it("falls back to home when the sign-in carried no return path", async () => {
    mockOidcAuth({ isAuthenticated: true, user: { state: undefined } })

    renderWithProviders(<Callback />)

    await waitFor(() => {
      expect(window.location.pathname).toBe("/")
    })
  })

  it("does not navigate while the callback is still processing", () => {
    mockOidcAuth({ isAuthenticated: false })

    renderWithProviders(<Callback />)

    expect(window.location.pathname).toBe("/callback")
  })

  it("replaces the callback entry so Back does not re-enter it", async () => {
    mockOidcAuth({ isAuthenticated: true, user: { state: { returnTo: "/budget" } } })

    renderWithProviders(<Callback />)

    await waitFor(() => expect(window.location.pathname).toBe("/budget"))

    window.history.back()
    await waitFor(() => expect(window.location.pathname).not.toBe("/callback"))
  })
})
