import { BrowserRouter, Routes, Route } from "react-router-dom"
import { AuthProvider } from "@/contexts/AuthContext"
import { Layout } from "@/components/layout/Layout"
import { Dashboard } from "@/pages/Dashboard"
import { Ledgers } from "@/pages/Ledgers"
import { Accounts } from "@/pages/Accounts"
import { Transactions } from "@/pages/Transactions"
import { Settings } from "@/pages/Settings"
import { Callback } from "@/pages/Callback"

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/callback" element={<Callback />} />
          <Route path="/" element={<Layout />}>
            <Route index element={<Dashboard />} />
            <Route path="ledgers" element={<Ledgers />} />
            <Route path="ledgers/:ledgerId/accounts" element={<Accounts />} />
            <Route path="accounts" element={<Accounts />} />
            <Route path="ledgers/:ledgerId/transactions" element={<Transactions />} />
            <Route path="transactions" element={<Transactions />} />
            <Route path="settings" element={<Settings />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

export default App
