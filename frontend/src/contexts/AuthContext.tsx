import { createContext, useContext, useEffect, type ReactNode } from "react"
import { AuthProvider as OidcAuthProvider, useAuth as useOidcAuth } from "react-oidc-context"
import { User, UserManager, OidcClient, WebStorageStateStore } from "oidc-client-ts"
import { apiClient } from "@/api/client"
import { isNativePlatform } from "@/lib/capacitor"
import { NativeStorage } from "@/lib/native-storage"

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

const AUTH_AUTHORITY = import.meta.env.VITE_AUTH_AUTHORITY || "http://localhost:9180/realms/Ricash"
const AUTH_CLIENT_ID = import.meta.env.VITE_AUTH_CLIENT_ID || "ricash-frontend"
const AUTH_AUDIENCE = import.meta.env.VITE_AUTH_AUDIENCE || ""

// On native platforms, use the custom URL scheme for redirects
const NATIVE_REDIRECT_URI = "com.ricash.app://callback"
const WEB_REDIRECT_URI = `${window.location.origin}/callback`
const REDIRECT_URI = isNativePlatform() ? NATIVE_REDIRECT_URI : WEB_REDIRECT_URI

// Configure OIDC client
const oidcConfig = {
  authority: AUTH_AUTHORITY,
  client_id: AUTH_CLIENT_ID,
  redirect_uri: REDIRECT_URI,
  post_logout_redirect_uri: isNativePlatform() ? NATIVE_REDIRECT_URI : window.location.origin,
  response_type: "code",
  scope: "openid profile email",

  // Enable automatic silent renew (web only — native uses refresh tokens)
  automaticSilentRenew: !isNativePlatform(),
  silent_redirect_uri: `${window.location.origin}/silent-renew.html`,

  // Use Capacitor Preferences on native, localStorage on web
  userStore: new WebStorageStateStore({
    store: isNativePlatform() ? new NativeStorage() : window.localStorage,
  }),

  // Token refresh settings
  accessTokenExpiringNotificationTimeInSeconds: 120, // Refresh 2 minutes before expiry

  // PKCE is enabled by default in oidc-client-ts

  // Additional settings for better compatibility
  loadUserInfo: true,

  // Pass audience as extra query param so Auth0 returns an API-scoped access token
  ...(AUTH_AUDIENCE ? { extraQueryParams: { audience: AUTH_AUDIENCE } } : {}),
}

// Create user manager and OIDC client instances
const userManager = new UserManager(oidcConfig)
const oidcClient = new OidcClient(oidcConfig)

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
  console.log("User loaded:", user.profile.preferred_username || user.profile.email)
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

  // Support both Keycloak (realm_access.roles) and Auth0 (https://ricash.app/roles) claim formats
  const realmAccess = profile.realm_access as { roles?: string[] } | undefined
  const roles =
    realmAccess?.roles ||
    (profile["https://ricash.app/roles"] as string[] | undefined) ||
    []

  const username =
    (profile.preferred_username as string) ||
    (profile.email as string) ||
    ""

  return {
    id: profile.sub || "",
    username,
    email: (profile.email as string) || "",
    name: (profile.name as string) || username,
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

  // On native platforms, listen for deep links from the OIDC redirect
  useEffect(() => {
    if (!isNativePlatform()) return

    let cleanup: (() => void) | undefined

    const setupListener = async () => {
      const { App } = await import("@capacitor/app")
      const listener = await App.addListener("appUrlOpen", async ({ url }) => {
        // Handle the OIDC callback URL (com.ricash.app://callback?code=...)
        if (url.startsWith(NATIVE_REDIRECT_URI)) {
          try {
            // Close the in-app browser
            const { Browser } = await import("@capacitor/browser")
            await Browser.close()
          } catch {
            // Browser might already be closed
          }

          // Let oidc-client-ts handle the callback by processing the response
          await userManager.signinRedirectCallback(url)
        }
      })
      cleanup = () => listener.remove()
    }

    setupListener()
    return () => cleanup?.()
  }, [])

  const logout = () => {
    auth.signoutRedirect()
  }

  const startLogin = async () => {
    if (isNativePlatform()) {
      // On native, generate the authorization URL and open it in an in-app browser
      const signinRequest = await oidcClient.createSigninRequest({})
      const { Browser } = await import("@capacitor/browser")
      await Browser.open({ url: signinRequest.url, presentationStyle: "popover" })
    } else {
      auth.signinRedirect()
    }
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
