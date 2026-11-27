import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"
import { ApiError } from "@/api/client"

export function useErrorHandler() {
  const { t } = useTranslation()

  return useCallback((error: unknown, operationKey?: string) => {
    console.error(operationKey ? `Failed to ${operationKey}:` : "Operation failed:", error)

    const operationMessage = operationKey
      ? t(`errors.${operationKey}`, { defaultValue: t("errors.generic") })
      : t("errors.generic")

    if (error instanceof ApiError) {
      if (error.status === 401) {
        // lib/session-recovery owns 401s (silent renew, else back to sign-in)
        // and shows the one session-expired toast; don't stack another here.
        return
      }
      if (error.status === 403) {
        toast.error(t("errors.forbidden"))
        return
      }
      if (error.status === 404) {
        toast.error(t("errors.notFound"))
        return
      }
      // The server's detail says what to fix ("Transaction is not balanced
      // for currency USD: ..."). It is English-only, so the localized message
      // stays as the title.
      if (error.status === 409) {
        toast.error(t("errors.conflict"), error.detail ? { description: error.detail } : undefined)
        return
      }
      if (error.status === 400) {
        toast.error(operationMessage, error.detail ? { description: error.detail } : undefined)
        return
      }
      if (error.status >= 500) {
        toast.error(t("errors.serverError"))
        return
      }
    }

    toast.error(operationMessage)
  }, [t])
}
