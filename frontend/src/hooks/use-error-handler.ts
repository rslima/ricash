import { useCallback } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"
import { ApiError } from "@/api/client"

export function useErrorHandler() {
  const { t } = useTranslation()

  return useCallback((error: unknown, operationKey?: string) => {
    console.error(operationKey ? `Failed to ${operationKey}:` : "Operation failed:", error)

    if (error instanceof ApiError) {
      if (error.status === 401) {
        toast.error(t("errors.unauthorized"))
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
      if (error.status === 409) {
        toast.error(t("errors.conflict"))
        return
      }
      if (error.status >= 500) {
        toast.error(t("errors.serverError"))
        return
      }
    }

    toast.error(operationKey ? t(`errors.${operationKey}`, { defaultValue: t("errors.generic") }) : t("errors.generic"))
  }, [t])
}
