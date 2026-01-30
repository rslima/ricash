import { createContext, useContext, type ReactNode } from "react"
import { AuthProvider as OidcAuthProvider, useAuth as useOidcAuth } from "react-oidc-context"
import { User, UserManager, WebStorageStateStore } from "oidc-client-ts"
import { apiClient } from "@/api/client"

interface AuthUser {
  id: string
  username: string
  email: string
  name: string
  roles: string[]
}

interface AuthContextType {
  user: AuthUser | null
  accessToken: string | null
  isAuthenticated: boolean
  isLoading: boolean
  logout: () => void
  startLogin: () => void
  exchangeCodeForToken: (code: string) => Promise<boolean>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

const KEYCLOAK_URL = import.meta.env.VITE_KEYCLOAK_URL || "http://localhost:9180"
const KEYCLOAK_REALM = import.meta.env.VITE_KEYCLOAK_REALM || "Ricash"
const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID || "ricash-frontend"

// Configure OIDC client
const oidcConfig = {
  authority: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`,
  client_id: KEYCLOAK_CLIENT_ID,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: "code",
  scope: "openid profile email",

  // Enable automatic silent renew
  automaticSilentRenew: true,
  silent_redirect_uri: `${window.location.origin}/silent-renew.html`,

  // Use localStorage for tokens
  userStore: new WebStorageStateStore({ store: window.localStorage }),

  // Token refresh settings
  accessTokenExpiringNotificationTimeInSeconds: 120, // Refresh 2 minutes before expiry

  // PKCE is enabled by default in oidc-client-ts

  // Additional settings for better compatibility
  loadUserInfo: true,

  // Metadata (optional - will be fetched from .well-known/openid-configuration)
  metadata: {
    issuer: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`,
    authorization_endpoint: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth`,
    token_endpoint: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
    userinfo_endpoint: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/userinfo`,
    end_session_endpoint: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout`,
    jwks_uri: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/certs`,
  },
}

// Create user manager instance
const userManager = new UserManager(oidcConfig)

// Set up event handlers for token management
userManager.events.addAccessTokenExpiring(() => {
  console.log("Access token expiring, attempting silent renewal...")
})

userManager.events.addAccessTokenExpired(() => {
  console.log("Access token expired")
})

userManager.events.addSilentRenewError((error) => {
  console.error("Silent renew error:", error)
})

userManager.events.addUserLoaded((user) => {
  console.log("User loaded:", user.profile.preferred_username)
  // Update API client with new token
  apiClient.setAccessToken(user.access_token)
})

userManager.events.addUserUnloaded(() => {
  console.log("User unloaded")
  apiClient.setAccessToken(null)
})

interface AuthProviderProps {
  children: ReactNode
}

function extractAuthUser(oidcUser: User | null | undefined): AuthUser | null {
  if (!oidcUser || !oidcUser.profile) return null

  const profile = oidcUser.profile
  const realmAccess = profile.realm_access as { roles?: string[] } | undefined
  const roles = realmAccess?.roles || []

  return {
    id: profile.sub || "",
    username: (profile.preferred_username as string) || "",
    email: (profile.email as string) || "",
    name: (profile.name as string) || (profile.preferred_username as string) || "",
    roles,
  }
}

function AuthProviderWrapper({ children }: AuthProviderProps) {
  const auth = useOidcAuth()

  const authUser = extractAuthUser(auth.user)
  const accessToken = auth.user?.access_token || null

  // Update API client token whenever it changes
  if (accessToken) {
    apiClient.setAccessToken(accessToken)
  } else {
    apiClient.setAccessToken(null)
  }

  const logout = () => {
    auth.signoutRedirect()
  }

  const startLogin = () => {
    auth.signinRedirect()
  }

  // For compatibility with existing code - this is handled by the library now
  const exchangeCodeForToken = async (): Promise<boolean> => {
    // The library handles this automatically, but we keep the function for compatibility
    return true
  }

  const value: AuthContextType = {
    user: authUser,
    accessToken,
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    logout,
    startLogin,
    exchangeCodeForToken,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function AuthProvider({ children }: AuthProviderProps) {
  return (
    <OidcAuthProvider
      userManager={userManager}
      onSigninCallback={() => {
        // After successful sign-in, navigate to home
        window.history.replaceState({}, document.title, window.location.pathname)
      }}
    >
      <AuthProviderWrapper>{children}</AuthProviderWrapper>
    </OidcAuthProvider>
  )
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
