import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from "react"
import { apiClient } from "@/api/client"

interface User {
  id: string
  username: string
  email: string
  name: string
  roles: string[]
}

interface AuthContextType {
  user: User | null
  accessToken: string | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (token: string) => void
  logout: () => void
  startLogin: () => void
  exchangeCodeForToken: (code: string) => Promise<boolean>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:9180"
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || "Ricash"
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "ricash-frontend"

interface AuthProviderProps {
  children: ReactNode
}

function parseJwt(token: string): Record<string, unknown> | null {
  try {
    const base64Url = token.split(".")[1]
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/")
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split("")
        .map((c) => "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2))
        .join("")
    )
    return JSON.parse(jsonPayload)
  } catch {
    return null
  }
}

function extractUserFromToken(token: string): User | null {
  const payload = parseJwt(token)
  if (!payload) return null

  const realmAccess = payload.realm_access as { roles?: string[] } | undefined
  const roles = realmAccess?.roles || []

  return {
    id: payload.sub as string,
    username: payload.preferred_username as string,
    email: payload.email as string,
    name: (payload.name as string) || (payload.preferred_username as string),
    roles,
  }
}

// PKCE helper functions
function generateCodeVerifier(): string {
  const array = new Uint8Array(32)
  crypto.getRandomValues(array)
  return base64UrlEncode(array)
}

function base64UrlEncode(buffer: Uint8Array): string {
  let binary = ""
  for (let i = 0; i < buffer.length; i++) {
    binary += String.fromCharCode(buffer[i])
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "")
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(verifier)
  const hash = await crypto.subtle.digest("SHA-256", data)
  return base64UrlEncode(new Uint8Array(hash))
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null)
  const [accessToken, setAccessToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const storedToken = localStorage.getItem("access_token")
    if (storedToken) {
      const tokenPayload = parseJwt(storedToken)
      if (tokenPayload && tokenPayload.exp) {
        const isExpired = Date.now() >= (tokenPayload.exp as number) * 1000
        if (!isExpired) {
          setAccessToken(storedToken)
          setUser(extractUserFromToken(storedToken))
          apiClient.setAccessToken(storedToken)
        } else {
          localStorage.removeItem("access_token")
        }
      }
    }
    setIsLoading(false)
  }, [])

  const login = useCallback((token: string) => {
    localStorage.setItem("access_token", token)
    setAccessToken(token)
    setUser(extractUserFromToken(token))
    apiClient.setAccessToken(token)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem("access_token")
    setAccessToken(null)
    setUser(null)
    apiClient.setAccessToken(null)

    // Redirect to Keycloak logout
    const logoutUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout?client_id=${KEYCLOAK_CLIENT_ID}&post_logout_redirect_uri=${encodeURIComponent(window.location.origin)}`
    window.location.href = logoutUrl
  }, [])

  const startLogin = useCallback(async () => {
    const codeVerifier = generateCodeVerifier()
    const codeChallenge = await generateCodeChallenge(codeVerifier)
    sessionStorage.setItem("pkce_code_verifier", codeVerifier)

    const redirectUri = encodeURIComponent(window.location.origin + "/callback")
    const authUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth?client_id=${KEYCLOAK_CLIENT_ID}&redirect_uri=${redirectUri}&response_type=code&scope=openid profile email&code_challenge=${codeChallenge}&code_challenge_method=S256`
    window.location.href = authUrl
  }, [])

  const exchangeCodeForToken = useCallback(async (code: string): Promise<boolean> => {
    const codeVerifier = sessionStorage.getItem("pkce_code_verifier")
    if (!codeVerifier) {
      console.error("No code verifier found")
      return false
    }

    try {
      const tokenUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`
      const response = await fetch(tokenUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          client_id: KEYCLOAK_CLIENT_ID,
          code,
          redirect_uri: window.location.origin + "/callback",
          code_verifier: codeVerifier,
        }),
      })

      if (!response.ok) {
        console.error("Token exchange failed:", await response.text())
        return false
      }

      const data = await response.json()
      sessionStorage.removeItem("pkce_code_verifier")
      login(data.access_token)
      return true
    } catch (error) {
      console.error("Token exchange error:", error)
      return false
    }
  }, [login])

  const value: AuthContextType = {
    user,
    accessToken,
    isAuthenticated: !!user,
    isLoading,
    login,
    logout,
    startLogin,
    exchangeCodeForToken,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}

