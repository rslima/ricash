import { Component, type ErrorInfo, type ReactNode } from "react"
import { Translation } from "react-i18next"
import { AlertTriangle } from "lucide-react"
import { Button } from "@/components/ui/button"

interface RouteErrorBoundaryProps {
  children: ReactNode
  /** Clears a caught error when it changes, e.g. the pathname after navigating away. */
  resetKey?: unknown
}

interface RouteErrorBoundaryState {
  error: Error | null
}

/**
 * Catches render errors and failed lazy-chunk loads in the routed content, so
 * the header and navigation stay usable instead of the whole app going blank.
 */
export class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): RouteErrorBoundaryState {
    return { error }
  }

  componentDidUpdate(prevProps: RouteErrorBoundaryProps) {
    if (this.state.error && prevProps.resetKey !== this.props.resetKey) {
      this.setState({ error: null })
    }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // Keep the component stack in production logs; React's own report only
    // names the boundary.
    console.error("Route render failed", error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <Translation>
        {(t) => (
          <div role="alert" className="flex flex-col items-center justify-center py-12 text-center">
            <AlertTriangle className="h-12 w-12 text-destructive mb-4" />
            <h3 className="text-lg font-semibold">{t("errorBoundary.title")}</h3>
            <p className="text-sm text-muted-foreground mb-4 max-w-md">{t("errorBoundary.description")}</p>
            <Button onClick={() => window.location.reload()}>{t("errorBoundary.reload")}</Button>
          </div>
        )}
      </Translation>
    )
  }
}
