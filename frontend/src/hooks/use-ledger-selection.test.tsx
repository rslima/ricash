import type { ReactNode } from "react"
import { describe, it, expect, vi, beforeEach } from "vitest"
import { act, renderHook, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router"
import { useLedgerSelection } from "./use-ledger-selection"
import * as ledgersApi from "@/api/ledgers"
import { mockUseAuth, mockAuthenticatedUser } from "@/test/test-utils"
import { makeLedger } from "@/test/fixtures"

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
}))

import { useAuth } from "@/contexts/AuthContext"

const personalLedger = makeLedger()
const businessLedger = makeLedger({
  id: "ledger-2",
  attributes: { slug: "business", name: "Business", currency: "EUR" },
})

/**
 * Providers renderHook needs: fresh QueryClient (no retries, no cache bleed)
 * plus a router. `initialPath`/`routePath` let route-param tests mount the
 * hook under a `/:ledgerSlug` route.
 */
function createWrapper(initialPath = "/", routePath = "*") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path={routePath} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    )
  }
}

describe("useLedgerSelection", () => {
  beforeEach(() => {
    vi.mocked(useAuth).mockReturnValue(
      mockUseAuth({ isAuthenticated: true, user: mockAuthenticatedUser, accessToken: "test-token" })
    )
  })

  it("selects the first ledger by default once loaded", async () => {
    vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [personalLedger, businessLedger] })

    const { result } = renderHook(() => useLedgerSelection(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.selectedLedgerSlug).toBe("personal-finance"))
    expect(result.current.selectedLedger).toEqual(personalLedger)
    expect(result.current.ledgerCurrency).toBe("USD")
    expect(result.current.ledgers).toHaveLength(2)
  })

  it("exposes no selection and the default currency with an empty ledger list", async () => {
    vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [] })

    const { result } = renderHook(() => useLedgerSelection(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.ledgers).toEqual([])
    expect(result.current.selectedLedgerSlug).toBeNull()
    expect(result.current.selectedLedger).toBeUndefined()
    expect(result.current.ledgerCurrency).toBe("BRL")
  })

  it("switches ledgers via setSelectedLedgerSlug", async () => {
    vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [personalLedger, businessLedger] })

    const { result } = renderHook(() => useLedgerSelection(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.selectedLedgerSlug).toBe("personal-finance"))

    act(() => result.current.setSelectedLedgerSlug("business"))

    expect(result.current.selectedLedgerSlug).toBe("business")
    expect(result.current.selectedLedger).toEqual(businessLedger)
    expect(result.current.ledgerCurrency).toBe("EUR")
  })

  it("initializes the selection from the route param", async () => {
    vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [personalLedger, businessLedger] })

    const { result } = renderHook(() => useLedgerSelection(), {
      wrapper: createWrapper("/ledgers/business", "/ledgers/:ledgerSlug"),
    })

    await waitFor(() => expect(result.current.selectedLedger).toEqual(businessLedger))
    expect(result.current.selectedLedgerSlug).toBe("business")
    expect(result.current.ledgerCurrency).toBe("EUR")
  })

  it("does not fetch ledgers when unauthenticated", async () => {
    vi.mocked(useAuth).mockReturnValue(mockUseAuth())
    vi.mocked(ledgersApi.getLedgers).mockResolvedValue({ data: [personalLedger] })

    const { result } = renderHook(() => useLedgerSelection(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(ledgersApi.getLedgers).not.toHaveBeenCalled()
    expect(result.current.ledgers).toEqual([])
    expect(result.current.selectedLedgerSlug).toBeNull()
  })

  it("surfaces the query error state", async () => {
    const failure = new Error("boom")
    vi.mocked(ledgersApi.getLedgers).mockRejectedValue(failure)

    const { result } = renderHook(() => useLedgerSelection(), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBe(failure)
    expect(result.current.ledgers).toEqual([])
  })
})
