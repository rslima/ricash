import { useEffect, useRef } from "react"
import { isNativePlatform } from "@/lib/capacitor"
import { userManager, NATIVE_REDIRECT_URI } from "@/lib/oidc"
import { resolveReturnTo } from "@/lib/auth-return"
import { publishReturnTo } from "@/lib/native-auth-return"

/**
 * On native platforms, listens for the OIDC deep-link redirect
 * (com.ricash.app://callback?code=...), closes the in-app browser, and hands
 * the URL to oidc-client-ts. No-op on web.
 */
export function useNativeAuthCallback(onError: (message: string) => void) {
  // Ref keeps the listener registered once while always calling the latest handler.
  const onErrorRef = useRef(onError)
  onErrorRef.current = onError

  useEffect(() => {
    if (!isNativePlatform()) return

    let cleanup: (() => void) | undefined

    const setupListener = async () => {
      const { App } = await import("@capacitor/app")
      const listener = await App.addListener("appUrlOpen", async ({ url }) => {
        if (url.startsWith(NATIVE_REDIRECT_URI)) {
          try {
            // Close the in-app browser
            const { Browser } = await import("@capacitor/browser")
            await Browser.close()
          } catch {
            // Browser might already be closed
          }

          // Let oidc-client-ts handle the callback by processing the response
          try {
            const user = await userManager.signinRedirectCallback(url)
            // Usually a no-op: the webview never left the page, so this
            // resolves to where the user already is. It matters when the app
            // was reloaded or cold-started while the browser was open.
            publishReturnTo(resolveReturnTo(user.state))
          } catch (error) {
            console.error("Sign-in callback error:", error)
            onErrorRef.current(error instanceof Error ? error.message : "Authentication callback failed")
          }
        }
      })
      cleanup = () => listener.remove()
    }

    setupListener()
    return () => cleanup?.()
  }, [])
}
