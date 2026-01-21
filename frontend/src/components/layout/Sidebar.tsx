import { NavLink } from "react-router-dom"
import {
  LayoutDashboard,
  BookOpen,
  Wallet,
  ArrowLeftRight,
  Settings,
  ChevronLeft,
  ChevronRight,
} from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  TooltipProvider,
} from "@/components/ui/tooltip"

interface SidebarProps {
  collapsed: boolean
  onToggle: () => void
}

const navigation = [
  { name: "Dashboard", href: "/", icon: LayoutDashboard },
  { name: "Ledgers", href: "/ledgers", icon: BookOpen },
  { name: "Accounts", href: "/accounts", icon: Wallet },
  { name: "Transactions", href: "/transactions", icon: ArrowLeftRight },
  { name: "Settings", href: "/settings", icon: Settings },
]

export function Sidebar({ collapsed, onToggle }: SidebarProps) {
  return (
    <TooltipProvider delayDuration={0}>
      <aside
        className={cn(
          "flex flex-col border-r border-sidebar-border bg-sidebar-background transition-all duration-300",
          collapsed ? "w-16" : "w-64"
        )}
      >
        <div className="flex h-14 items-center border-b border-sidebar-border px-4">
          {!collapsed && (
            <span className="text-lg font-semibold text-sidebar-foreground">
              Ricash
            </span>
          )}
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggle}
            className={cn("ml-auto", collapsed && "mx-auto")}
          >
            {collapsed ? (
              <ChevronRight className="h-4 w-4" />
            ) : (
              <ChevronLeft className="h-4 w-4" />
            )}
          </Button>
        </div>

        <nav className="flex-1 space-y-1 p-2">
          {navigation.map((item) => {
            const NavItem = (
              <NavLink
                key={item.name}
                to={item.href}
                className={({ isActive }) =>
                  cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-sidebar-accent text-sidebar-accent-foreground"
                      : "text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground",
                    collapsed && "justify-center px-2"
                  )
                }
              >
                <item.icon className="h-5 w-5 shrink-0" />
                {!collapsed && <span>{item.name}</span>}
              </NavLink>
            )

            if (collapsed) {
              return (
                <Tooltip key={item.name}>
                  <TooltipTrigger asChild>{NavItem}</TooltipTrigger>
                  <TooltipContent side="right">{item.name}</TooltipContent>
                </Tooltip>
              )
            }

            return NavItem
          })}
        </nav>

        <div className="border-t border-sidebar-border p-4">
          {!collapsed && (
            <p className="text-xs text-muted-foreground">
              Personal Finance Manager
            </p>
          )}
        </div>
      </aside>
    </TooltipProvider>
  )
}
