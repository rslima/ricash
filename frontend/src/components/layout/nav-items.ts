import type { LucideIcon } from "lucide-react"
import {
  LayoutDashboard,
  BookOpen,
  Wallet,
  ArrowLeftRight,
  PiggyBank,
  FolderOpen,
  Briefcase,
  DollarSign,
  PieChart,
  FileBarChart,
  TrendingUp,
  Settings,
} from "lucide-react"

export interface NavItem {
  /** i18n suffix: label is t(`nav.${key}`). */
  key: string
  href: string
  icon: LucideIcon
  /** "primary" items surface as bottom-nav tabs; "more" items live in the sheet. */
  group: "primary" | "more"
}

/**
 * Single source of truth for app navigation. The Sidebar renders the whole
 * list in this order; the mobile BottomNav renders group === "primary" as
 * tabs and group === "more" in the More sheet (each preserving this order).
 */
export const navItems: NavItem[] = [
  { key: "dashboard", href: "/", icon: LayoutDashboard, group: "primary" },
  { key: "ledgers", href: "/ledgers", icon: BookOpen, group: "more" },
  { key: "accounts", href: "/accounts", icon: Wallet, group: "primary" },
  { key: "transactions", href: "/transactions", icon: ArrowLeftRight, group: "primary" },
  { key: "budget", href: "/budget", icon: PiggyBank, group: "primary" },
  { key: "envelopes", href: "/envelopes", icon: FolderOpen, group: "more" },
  { key: "instruments", href: "/instruments", icon: Briefcase, group: "more" },
  { key: "instrumentPrices", href: "/instrument-prices", icon: DollarSign, group: "more" },
  { key: "portfolio", href: "/portfolio", icon: PieChart, group: "primary" },
  { key: "reports", href: "/reports", icon: FileBarChart, group: "more" },
  { key: "exchangeRates", href: "/exchange-rates", icon: TrendingUp, group: "more" },
  { key: "settings", href: "/settings", icon: Settings, group: "more" },
]
