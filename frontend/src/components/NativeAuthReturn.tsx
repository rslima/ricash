import { useEffect, useSyncExternalStore } from "react"
import { useNavigate } from "react-router"
import { consumeReturnTo, getPendingReturnTo, subscribeReturnTo } from "@/lib/native-auth-return"

/**
 * Restores the pre-login page after a native deep-link sign-in.
 *
 * Renders nothing. It exists to give the native callback — which runs outside
 * the router — a way to navigate. Inert on web, where the sign-in round-trip
 * goes through the /callback route instead.
 */
export function NativeAuthReturn() {
  const navigate = useNavigate()
  const returnTo = useSyncExternalStore(subscribeReturnTo, getPendingReturnTo, () => null)

  useEffect(() => {
    if (returnTo === null) return
    // Clear first: navigating re-renders, and a still-pending path would
    // otherwise re-trigger the effect.
    consumeReturnTo()
    navigate(returnTo, { replace: true })
  }, [returnTo, navigate])

  return null
}
