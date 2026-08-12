import { useEffect, useRef } from "react"
import { isNativePlatform } from "@/lib/capacitor"
import { userManager, NATIVE_REDIRECT_URI } from "@/lib/oidc"
import { resolveReturnTo } from "@/lib/auth-return"
import { publishReturnTo } from "@/lib/native-auth-return"

/**
 * On native platforms, listens for the OIDC deep-link redirect
 * (com.ricash.app://callback?code=...), closes the in-app browser, and hands
 * the URL to oidc-client-ts. No-op on web.
 *
 * Reads the link from both channels: appUrlOpen, and getLaunchUrl for one that
 * started the app. Belt and braces rather than a fix for an observed bug — on
 * Capacitor 8 / Android 15 appUrlOpen alone already catches a cold start, even
 * though the listener registers in an effect (verified on device). getLaunchUrl
 * covers platforms that might only deliver the link that way; iOS is untested.
 *
 * Both channels firing is the normal case there, not an edge case, so the
 * dedupe below is what keeps that from double-processing.
 */
export function useNativeAuthCallback(onError: (message: string) => void) {
  // Ref keeps the listener registered once while always calling the latest handler.
  const onErrorRef = useRef(onError)
  onErrorRef.current = onError

  useEffect(() => {
    if (!isNativePlatform()) return

    let cancelled = false
    let cleanup: (() => void) | undefined

    // A launch link is delivered by both channels (observed on Android 15).
    // The OIDC state behind it is single-use, so a second pass fails and
    // reports a bogus error over a sign-in that actually succeeded — handle
    // each URL once.
    const handled = new Set<string>()

    const handleRedirect = async (url: string) => {
      if (!url.startsWith(NATIVE_REDIRECT_URI) || handled.has(url)) return
      handled.add(url)

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
        if (cancelled) return
        // Usually a no-op: the webview never left the page, so this
        // resolves to where the user already is. It matters when the app
        // was reloaded or cold-started while the browser was open.
        publishReturnTo(resolveReturnTo(user.state))
      } catch (error) {
        console.error("Sign-in callback error:", error)
        if (cancelled) return
        onErrorRef.current(error instanceof Error ? error.message : "Authentication callback failed")
      }
    }

    const setupListener = async () => {
      const { App } = await import("@capacitor/app")

      // Register before asking for the launch URL so a link arriving in
      // between is caught by one channel or the other.
      const listener = await App.addListener("appUrlOpen", ({ url }) => {
        void handleRedirect(url)
      })
      if (cancelled) {
        listener.remove()
        return
      }
      cleanup = () => listener.remove()

      const launch = await App.getLaunchUrl()
      if (launch?.url) await handleRedirect(launch.url)
    }

    setupListener()
    return () => {
      cancelled = true
      cleanup?.()
    }
  }, [])
}
