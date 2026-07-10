import { createContext, useContext } from "react"

export interface AuthUser {
  id: string
  username: string
  email: string
  name: string
  roles: string[]
}

export interface AuthContextType {
  user: AuthUser | null
  accessToken: string | null
  isAuthenticated: boolean
  isLoading: boolean
  loginError: string | null
  logout: () => void
  startLogin: () => void
}

// Context + hook live apart from the provider component (see AuthProvider.tsx)
// so Fast Refresh can hot-swap the provider file, and so page tests can mock
// this module with just { useAuth }.
export const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
