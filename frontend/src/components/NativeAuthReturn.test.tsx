import { describe, it, expect, afterEach } from "vitest"
import { act, waitFor } from "@testing-library/react"
import { renderWithProviders } from "@/test/test-utils"
import { consumeReturnTo, getPendingReturnTo, publishReturnTo } from "@/lib/native-auth-return"
import { NativeAuthReturn } from "./NativeAuthReturn"

afterEach(() => {
  consumeReturnTo()
  window.history.pushState({}, "", "/")
})

describe("NativeAuthReturn", () => {
  it("navigates when the native callback publishes a path", async () => {
    renderWithProviders(<NativeAuthReturn />)
    expect(window.location.pathname).toBe("/")

    act(() => publishReturnTo("/transactions?page=3"))

    await waitFor(() => {
      expect(window.location.pathname + window.location.search).toBe("/transactions?page=3")
    })
  })

  it("consumes the path so it does not navigate twice", async () => {
    renderWithProviders(<NativeAuthReturn />)

    act(() => publishReturnTo("/budget"))
    await waitFor(() => expect(window.location.pathname).toBe("/budget"))

    expect(getPendingReturnTo()).toBeNull()

    // A later in-app navigation must not be undone by a stale pending path.
    act(() => {
      window.history.pushState({}, "", "/accounts")
    })
    await waitFor(() => expect(window.location.pathname).toBe("/accounts"))
  })

  it("stays put when nothing is published", async () => {
    window.history.pushState({}, "", "/reports")
    renderWithProviders(<NativeAuthReturn />)

    await waitFor(() => expect(window.location.pathname).toBe("/reports"))
  })

  it("replaces the entry so Back does not return to the pre-login page", async () => {
    renderWithProviders(<NativeAuthReturn />)

    act(() => publishReturnTo("/portfolio"))
    await waitFor(() => expect(window.location.pathname).toBe("/portfolio"))

    window.history.back()
    await waitFor(() => expect(window.location.pathname).not.toBe("/portfolio"))
  })
})
