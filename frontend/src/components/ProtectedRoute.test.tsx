import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ProtectedRoute } from "./ProtectedRoute"

vi.mock("@/contexts/AuthContext", () => ({
  useAuth: vi.fn(),
}))

vi.mock("@/api/ledgers", () => ({
  getLedgers: vi.fn().mockResolvedValue({ data: [] }),
}))

import { useAuth } from "@/contexts/AuthContext"

function renderProtected() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <MemoryRouter initialEntries={["/"]}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route index element={<div>protected content</div>} />
          </Route>
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>
  )
}

function mockAuth(overrides: Partial<ReturnType<typeof useAuth>>) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated: false,
    user: null,
    accessToken: null,
    isLoading: false,
    loginError: null,
    logout: vi.fn(),
    startLogin: vi.fn(),
    ...overrides,
  } as ReturnType<typeof useAuth>)
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("shows a loading state while the session restores", () => {
    mockAuth({ isLoading: true })

    const { container } = renderProtected()

    expect(screen.queryByText("protected content")).not.toBeInTheDocument()
    expect(container.querySelector('[data-slot="skeleton"], .animate-pulse')).toBeTruthy()
  })

  it("shows the sign-in card when unauthenticated", () => {
    mockAuth({ isAuthenticated: false })

    renderProtected()

    expect(screen.getByText("Sign in Required")).toBeInTheDocument()
    expect(screen.queryByText("protected content")).not.toBeInTheDocument()
  })

  it("renders the routed page when authenticated", () => {
    mockAuth({ isAuthenticated: true })

    renderProtected()

    expect(screen.getByText("protected content")).toBeInTheDocument()
  })
})
