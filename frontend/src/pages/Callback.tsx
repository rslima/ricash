import { useEffect } from "react"
import { useNavigate } from "react-router"
import { useAuth as useOidcAuth } from "react-oidc-context"
import { useTranslation } from "react-i18next"
import { resolveReturnTo } from "@/lib/auth-return"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Loader2 } from "lucide-react"

export function Callback() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const auth = useOidcAuth()

  useEffect(() => {
    // The library handles the callback automatically. Once authentication is
    // complete, return the user to the page they started from — falling back
    // to home when there is no usable return path.
    if (auth.isAuthenticated) {
      navigate(resolveReturnTo(auth.user?.state), { replace: true })
    }
  }, [auth.isAuthenticated, auth.user, navigate])

  // Show error if authentication failed
  if (auth.error) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center p-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle className="text-destructive">{t("auth.authFailed")}</CardTitle>
            <CardDescription>{auth.error.message}</CardDescription>
          </CardHeader>
          <CardContent className="text-center">
            <button
              onClick={() => navigate("/")}
              className="text-sm text-primary hover:underline"
            >
              {t("auth.returnHome")}
            </button>
          </CardContent>
        </Card>
      </div>
    )
  }

  // Show loading while processing the callback
  return (
    <div className="flex min-h-screen flex-col items-center justify-center p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle>{t("auth.signingIn")}</CardTitle>
          <CardDescription>{t("auth.signingInDescription")}</CardDescription>
        </CardHeader>
        <CardContent className="flex justify-center py-6">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </CardContent>
      </Card>
    </div>
  )
}
