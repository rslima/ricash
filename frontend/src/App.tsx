import { lazy, Suspense } from "react"
import { BrowserRouter, Routes, Route } from "react-router-dom"
import { QueryClientProvider } from "@tanstack/react-query"
import { ReactQueryDevtools } from "@tanstack/react-query-devtools"
import { queryClient } from "@/lib/queryClient"
import { AuthProvider } from "@/contexts/AuthContext"
import { ThemeProvider } from "@/contexts/ThemeContext"
import { Toaster } from "@/components/ui/sonner"
import { Layout } from "@/components/layout/Layout"
import { TableSkeleton } from "@/components/ui/table-skeleton"
// The OIDC callback stays eager: it must not depend on a lazy chunk mid-login.
import { Callback } from "@/pages/Callback"

// Pages are code-split per route; they use named exports, hence the remap.
const Dashboard = lazy(() => import("@/pages/Dashboard").then((m) => ({ default: m.Dashboard })))
const Ledgers = lazy(() => import("@/pages/Ledgers").then((m) => ({ default: m.Ledgers })))
const Accounts = lazy(() => import("@/pages/Accounts").then((m) => ({ default: m.Accounts })))
const AccountTransactions = lazy(() =>
  import("@/pages/AccountTransactions").then((m) => ({ default: m.AccountTransactions }))
)
const Transactions = lazy(() => import("@/pages/Transactions").then((m) => ({ default: m.Transactions })))
const ExchangeRates = lazy(() => import("@/pages/ExchangeRates").then((m) => ({ default: m.ExchangeRates })))
const Instruments = lazy(() => import("@/pages/Instruments").then((m) => ({ default: m.Instruments })))
const InstrumentPrices = lazy(() =>
  import("@/pages/InstrumentPrices").then((m) => ({ default: m.InstrumentPrices }))
)
const Portfolio = lazy(() => import("@/pages/Portfolio").then((m) => ({ default: m.Portfolio })))
const Settings = lazy(() => import("@/pages/Settings").then((m) => ({ default: m.Settings })))
const Budget = lazy(() => import("@/pages/Budget").then((m) => ({ default: m.Budget })))
const Envelopes = lazy(() => import("@/pages/Envelopes").then((m) => ({ default: m.Envelopes })))
const Reports = lazy(() => import("@/pages/Reports").then((m) => ({ default: m.Reports })))
const CategoryTransactions = lazy(() =>
  import("@/pages/CategoryTransactions").then((m) => ({ default: m.CategoryTransactions }))
)

// Pages reachable both unscoped (/x) and ledger-scoped (/ledgers/:ledgerSlug/x).
const scopedRoutes = [
  { path: "accounts", element: <Accounts /> },
  { path: "transactions", element: <Transactions /> },
  { path: "budget", element: <Budget /> },
  { path: "envelopes", element: <Envelopes /> },
  { path: "instruments", element: <Instruments /> },
  { path: "instrument-prices", element: <InstrumentPrices /> },
  { path: "portfolio", element: <Portfolio /> },
]

function App() {
  return (
    <QueryClientProvider client={queryClient}>
    <ThemeProvider>
    <AuthProvider>
      <BrowserRouter>
        <Suspense fallback={<TableSkeleton />}>
          <Routes>
            <Route path="/callback" element={<Callback />} />
            <Route path="/" element={<Layout />}>
              <Route index element={<Dashboard />} />
              <Route path="ledgers" element={<Ledgers />} />
              <Route path="ledgers/:ledgerSlug/accounts/:accountId/transactions" element={<AccountTransactions />} />
              <Route path="ledgers/:ledgerSlug/reports/categories/:accountId" element={<CategoryTransactions />} />
              {scopedRoutes.map(({ path, element }) => (
                <Route key={path} path={path} element={element} />
              ))}
              {scopedRoutes.map(({ path, element }) => (
                <Route key={`scoped-${path}`} path={`ledgers/:ledgerSlug/${path}`} element={element} />
              ))}
              <Route path="exchange-rates" element={<ExchangeRates />} />
              <Route path="reports" element={<Reports />} />
              <Route path="settings" element={<Settings />} />
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
      <Toaster />
    </AuthProvider>
    </ThemeProvider>
    {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  )
}

export default App
