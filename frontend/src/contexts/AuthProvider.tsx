import { useState, type ReactNode } from "react"
import { AuthProvider as OidcAuthProvider, useAuth as useOidcAuth } from "react-oidc-context"
import { User } from "oidc-client-ts"
import { apiClient } from "@/api/client"
import { isNativePlatform } from "@/lib/capacitor"
import { userManager, oidcClient } from "@/lib/oidc"
import { captureReturnTo } from "@/lib/auth-return"
import { useNativeAuthCallback } from "@/hooks/use-native-auth-callback"
import { AuthContext, type AuthContextType, type AuthUser } from "./AuthContext"

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
  const [loginError, setLoginError] = useState<string | null>(null)

  const authUser = extractAuthUser(auth.user)
  const accessToken = auth.user?.access_token || null

  // Single token→apiClient sync point. Deliberately during render, not in an
  // effect: child components' query effects run before a parent's effect
  // would, so an effect here would let the first authenticated queries fire
  // with a stale token. The assignment is idempotent, so re-renders are safe.
  apiClient.setAccessToken(accessToken)

  // On native platforms, handle the OIDC deep-link redirect.
  useNativeAuthCallback(setLoginError)

  const logout = () => {
    auth.signoutRedirect()
  }

  const startLogin = async () => {
    setLoginError(null)
    try {
      if (isNativePlatform()) {
        // On native, generate the authorization URL and open it in an in-app browser
        const signinRequest = await oidcClient.createSigninRequest({})
        const { Browser } = await import("@capacitor/browser")
        await Browser.open({ url: signinRequest.url })
      } else {
        // Remember the current page so /callback can return the user to it
        // instead of dropping them on the dashboard.
        await auth.signinRedirect({ state: { returnTo: captureReturnTo() } })
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "Login failed"
      console.error("Login error:", error)
      setLoginError(message)
    }
  }

  const value: AuthContextType = {
    user: authUser,
    accessToken,
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    loginError,
    logout,
    startLogin,
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
