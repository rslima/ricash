import { Outlet } from "react-router-dom"
import { useTranslation } from "react-i18next"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useAuth } from "@/contexts/AuthContext"
import { LedgerProvider } from "@/contexts/LedgerContext"

/**
 * Single auth gate for every app page: shows a loading skeleton while the
 * OIDC session restores, the sign-in card when unauthenticated, and mounts
 * the ledger context around the routed page otherwise.
 */
export function ProtectedRoute() {
  const { t } = useTranslation()
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  if (!isAuthenticated) {
    return (
      <div className="flex flex-col items-center justify-center h-full">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.signInRequired")}</CardTitle>
            <CardDescription>
              {t("auth.pleaseSignInGeneric")}
            </CardDescription>
          </CardHeader>
        </Card>
      </div>
    )
  }

  return (
    <LedgerProvider>
      <Outlet />
    </LedgerProvider>
  )
}
