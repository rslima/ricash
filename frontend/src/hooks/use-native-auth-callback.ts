import { useEffect, useRef } from "react"
import { isNativePlatform } from "@/lib/capacitor"
import { userManager, NATIVE_REDIRECT_URI } from "@/lib/oidc"

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
            await userManager.signinRedirectCallback(url)
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
