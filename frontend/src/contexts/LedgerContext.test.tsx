import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { LedgerProvider, useLedger } from "./LedgerContext"
import type { LedgerResource } from "@/api/types"

vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn(),
}))

import { getLedgers } from "@/api/ledgers"

function ledger(slug: string): LedgerResource {
  return {
    type: "ledgers",
    id: slug,
    attributes: {
      slug,
      name: slug,
      description: null,
      currency: "USD",
      createdAt: "2024-01-01T00:00:00Z",
      updatedAt: "2024-01-01T00:00:00Z",
    },
  } as unknown as LedgerResource
}

function CurrentLedgerProbe() {
  const { currentLedger } = useLedger()
  return <div>current: {currentLedger?.attributes.slug ?? "none"}</div>
}

function renderWithRoute(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route
            path="/ledgers/:ledgerSlug/accounts"
            element={<LedgerProvider><CurrentLedgerProbe /></LedgerProvider>}
          />
          <Route path="/" element={<LedgerProvider><CurrentLedgerProbe /></LedgerProvider>} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

describe("LedgerProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.mocked(getLedgers).mockResolvedValue({
      data: [ledger("alpha"), ledger("beta")],
    } as Awaited<ReturnType<typeof getLedgers>>)
  })

  it("prefers the URL ledger slug", async () => {
    localStorage.setItem("ricash.ledger", "alpha")

    renderWithRoute("/ledgers/beta/accounts")

    await waitFor(() => expect(screen.getByText("current: beta")).toBeInTheDocument())
    expect(localStorage.getItem("ricash.ledger")).toBe("beta")
  })

  it("falls back to the remembered selection", async () => {
    localStorage.setItem("ricash.ledger", "beta")

    renderWithRoute("/")

    await waitFor(() => expect(screen.getByText("current: beta")).toBeInTheDocument())
  })

  it("falls back to the first ledger", async () => {
    renderWithRoute("/")

    await waitFor(() => expect(screen.getByText("current: alpha")).toBeInTheDocument())
  })

  it("ignores a remembered slug that no longer exists", async () => {
    localStorage.setItem("ricash.ledger", "deleted-ledger")

    renderWithRoute("/")

    await waitFor(() => expect(screen.getByText("current: alpha")).toBeInTheDocument())
  })
})
