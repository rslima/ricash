import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

interface SignInRequiredProps {
  /** i18n key of the resource name interpolated into auth.pleaseSignIn, e.g. "nav.transactions". */
  resourceKey?: string
  /** Full description override for pages with bespoke copy (e.g. Settings). */
  description?: ReactNode
}

/** The centered auth-gate card every page shows when unauthenticated. */
export function SignInRequired({ resourceKey, description }: SignInRequiredProps) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center h-full">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle>{t("auth.signInRequired")}</CardTitle>
          <CardDescription>
            {description ??
              t("auth.pleaseSignIn", { resource: resourceKey ? t(resourceKey).toLowerCase() : "" })}
          </CardDescription>
        </CardHeader>
      </Card>
    </div>
  )
}
