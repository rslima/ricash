import { UserManager, OidcClient, WebStorageStateStore } from "oidc-client-ts"
import { isNativePlatform } from "@/lib/capacitor"
import { NativeStorage } from "@/lib/native-storage"

const AUTH_AUTHORITY = import.meta.env.VITE_AUTH_AUTHORITY || "http://localhost:9180/realms/Ricash"
const AUTH_CLIENT_ID = import.meta.env.VITE_AUTH_CLIENT_ID || "ricash-frontend"
const AUTH_AUDIENCE = import.meta.env.VITE_AUTH_AUDIENCE || ""

// On native platforms, the OIDC redirect round-trips through a custom URL scheme.
export const NATIVE_REDIRECT_URI = "com.ricash.app://callback"
const WEB_REDIRECT_URI = `${window.location.origin}/callback`
const REDIRECT_URI = isNativePlatform() ? NATIVE_REDIRECT_URI : WEB_REDIRECT_URI

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

/** Shared OIDC instances. Token → apiClient syncing lives in AuthContext, not here. */
export const userManager = new UserManager(oidcConfig)
export const oidcClient = new OidcClient(oidcConfig)

// Diagnostic logging for token lifecycle events.
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
})

userManager.events.addUserUnloaded(() => {
  console.log("User unloaded")
})
