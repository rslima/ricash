import { useState } from "react"
import { NavLink, useLocation } from "react-router-dom"
import { useTranslation } from "react-i18next"
import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  PiggyBank,
  PieChart,
  MoreHorizontal,
  BookOpen,
  FolderOpen,
  Briefcase,
  DollarSign,
  TrendingUp,
  Settings,
} from "lucide-react"
import { cn } from "@/lib/utils"
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from "@/components/ui/sheet"

const primaryTabs = [
  { key: "dashboard", href: "/", icon: LayoutDashboard },
  { key: "accounts", href: "/accounts", icon: Wallet },
  { key: "transactions", href: "/transactions", icon: ArrowLeftRight },
  { key: "budget", href: "/budget", icon: PiggyBank },
  { key: "portfolio", href: "/portfolio", icon: PieChart },
]

const moreItems = [
  { key: "ledgers", href: "/ledgers", icon: BookOpen },
  { key: "envelopes", href: "/envelopes", icon: FolderOpen },
  { key: "instruments", href: "/instruments", icon: Briefcase },
  { key: "instrumentPrices", href: "/instrument-prices", icon: DollarSign },
  { key: "exchangeRates", href: "/exchange-rates", icon: TrendingUp },
  { key: "settings", href: "/settings", icon: Settings },
]

export function BottomNav() {
  const { t } = useTranslation()
  const [moreOpen, setMoreOpen] = useState(false)
  const location = useLocation()

  const isMoreActive = moreItems.some(
    (item) => location.pathname === item.href || location.pathname.startsWith(item.href + "/")
  )

  return (
    <>
      <nav className="flex h-16 shrink-0 items-stretch border-t border-border bg-background safe-area-pb">
        {primaryTabs.map((item) => (
          <NavLink
            key={item.key}
            to={item.href}
            end={item.href === "/"}
            className={({ isActive }) =>
              cn(
                "flex flex-1 flex-col items-center justify-center gap-0.5 text-[10px] font-medium transition-colors",
                isActive
                  ? "text-primary"
                  : "text-muted-foreground"
              )
            }
          >
            <item.icon className="h-5 w-5" />
            <span>{t(`nav.${item.key}`)}</span>
          </NavLink>
        ))}
        <button
          onClick={() => setMoreOpen(true)}
          className={cn(
            "flex flex-1 flex-col items-center justify-center gap-0.5 text-[10px] font-medium transition-colors",
            isMoreActive ? "text-primary" : "text-muted-foreground"
          )}
        >
          <MoreHorizontal className="h-5 w-5" />
          <span>{t("nav.more")}</span>
        </button>
      </nav>

      <Sheet open={moreOpen} onOpenChange={setMoreOpen}>
        <SheetContent side="bottom" className="rounded-t-xl px-2 pb-8">
          <SheetHeader className="px-4">
            <SheetTitle>{t("nav.more")}</SheetTitle>
            <SheetDescription className="sr-only">
              {t("nav.moreDescription")}
            </SheetDescription>
          </SheetHeader>
          <div className="grid grid-cols-3 gap-2 pt-4">
            {moreItems.map((item) => (
              <NavLink
                key={item.key}
                to={item.href}
                onClick={() => setMoreOpen(false)}
                className={({ isActive }) =>
                  cn(
                    "flex flex-col items-center gap-1.5 rounded-lg p-3 text-xs font-medium transition-colors",
                    isActive
                      ? "bg-primary/10 text-primary"
                      : "text-muted-foreground hover:bg-muted"
                  )
                }
              >
                <item.icon className="h-6 w-6" />
                <span className="text-center">{t(`nav.${item.key}`)}</span>
              </NavLink>
            ))}
          </div>
        </SheetContent>
      </Sheet>
    </>
  )
}
