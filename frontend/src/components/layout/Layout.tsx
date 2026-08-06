import { Suspense, useState } from "react"
import { Outlet } from "react-router"
import { Sidebar } from "./Sidebar"
import { Header } from "./Header"
import { BottomNav } from "./BottomNav"
import { TableSkeleton } from "@/components/ui/table-skeleton"
import { useIsMobile } from "@/hooks/use-mobile"

export function Layout() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const isMobile = useIsMobile()

  // Suspense lives inside the shell so lazy page chunks load without
  // unmounting the header/nav.
  const outlet = (
    <Suspense fallback={<TableSkeleton />}>
      <Outlet />
    </Suspense>
  )

  if (isMobile) {
    return (
      <div className="flex h-[100dvh] flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto bg-muted/30 p-4">
          {outlet}
        </main>
        <BottomNav />
      </div>
    )
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(!sidebarCollapsed)}
      />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto bg-muted/30 p-6">
          {outlet}
        </main>
      </div>
    </div>
  )
}
