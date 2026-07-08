import { useEffect } from "react"
import { useNavigate } from "react-router-dom"
import { useAuth as useOidcAuth } from "react-oidc-context"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Loader2 } from "lucide-react"

export function Callback() {
  const navigate = useNavigate()
  const auth = useOidcAuth()

  useEffect(() => {
    // The library handles the callback automatically
    // Once authentication is complete, redirect to home
    if (auth.isAuthenticated) {
      navigate("/", { replace: true })
    }
  }, [auth.isAuthenticated, navigate])

  // Show error if authentication failed
  if (auth.error) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center p-4">
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle className="text-destructive">Authentication Failed</CardTitle>
            <CardDescription>{auth.error.message}</CardDescription>
          </CardHeader>
          <CardContent className="text-center">
            <button
              onClick={() => navigate("/")}
              className="text-sm text-primary hover:underline"
            >
              Return to home
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
          <CardTitle>Signing you in...</CardTitle>
          <CardDescription>Please wait while we complete authentication</CardDescription>
        </CardHeader>
        <CardContent className="flex justify-center py-6">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
        </CardContent>
      </Card>
    </div>
  )
}
