import type { ReactElement } from "react"
import { render } from "@testing-library/react"
import { BrowserRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { vi } from "vitest"
import type { AuthContextType, AuthUser } from "@/contexts/AuthContext"

/**
 * Renders a page/component under the providers page tests need.
 * Fresh QueryClient per render so cache never bleeds between tests; retries
 * off so a failing query surfaces immediately instead of after backoff.
 * No AuthProvider on purpose — it constructs a real OIDC UserManager, so page
 * tests vi.mock("@/contexts/AuthContext") and stub useAuth via mockUseAuth.
 */
export function renderWithProviders(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>
  )
}

export const mockAuthenticatedUser: AuthUser = {
  id: "user-1",
  username: "testuser",
  email: "test@example.com",
  name: "Test User",
  roles: [],
}

/**
 * Complete AuthContextType value for vi.mocked(useAuth).mockReturnValue(...).
 * Defaults to signed-out; pass overrides for the authenticated cases, e.g.
 * mockUseAuth({ isAuthenticated: true, user: mockAuthenticatedUser, accessToken: "test-token" }).
 */
export function mockUseAuth(overrides: Partial<AuthContextType> = {}): AuthContextType {
  return {
    user: null,
    accessToken: null,
    isAuthenticated: false,
    isLoading: false,
    loginError: null,
    logout: vi.fn(),
    startLogin: vi.fn(),
    exchangeCodeForToken: vi.fn(async () => true),
    ...overrides,
  }
}
