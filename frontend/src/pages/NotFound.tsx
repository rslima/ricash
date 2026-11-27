import { Link } from "react-router"
import { useTranslation } from "react-i18next"
import { FileQuestion } from "lucide-react"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/EmptyState"

/** Catch-all route inside the app shell for addresses no route matches. */
export function NotFound() {
  const { t } = useTranslation()
  return (
    <EmptyState
      icon={FileQuestion}
      title={t("notFound.title")}
      description={t("notFound.description")}
      action={
        <Button asChild>
          <Link to="/">{t("notFound.goHome")}</Link>
        </Button>
      }
    />
  )
}
