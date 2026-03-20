import { BrowserRouter, Routes, Route } from "react-router-dom"
import { AuthProvider } from "@/contexts/AuthContext"
import { Layout } from "@/components/layout/Layout"
import { Dashboard } from "@/pages/Dashboard"
import { Ledgers } from "@/pages/Ledgers"
import { Accounts } from "@/pages/Accounts"
import { AccountTransactions } from "@/pages/AccountTransactions"
import { Transactions } from "@/pages/Transactions"
import { ExchangeRates } from "@/pages/ExchangeRates"
import { Instruments } from "@/pages/Instruments"
import { InstrumentPrices } from "@/pages/InstrumentPrices"
import { Portfolio } from "@/pages/Portfolio"
import { Settings } from "@/pages/Settings"
import { Callback } from "@/pages/Callback"
import { Budget } from "@/pages/Budget"
import { Envelopes } from "@/pages/Envelopes"

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/callback" element={<Callback />} />
          <Route path="/" element={<Layout />}>
            <Route index element={<Dashboard />} />
            <Route path="ledgers" element={<Ledgers />} />
            <Route path="ledgers/:ledgerSlug/accounts" element={<Accounts />} />
            <Route path="ledgers/:ledgerSlug/accounts/:accountId/transactions" element={<AccountTransactions />} />
            <Route path="accounts" element={<Accounts />} />
            <Route path="ledgers/:ledgerSlug/transactions" element={<Transactions />} />
            <Route path="transactions" element={<Transactions />} />
            <Route path="budget" element={<Budget />} />
            <Route path="ledgers/:ledgerSlug/budget" element={<Budget />} />
            <Route path="envelopes" element={<Envelopes />} />
            <Route path="ledgers/:ledgerSlug/envelopes" element={<Envelopes />} />
            <Route path="exchange-rates" element={<ExchangeRates />} />
            <Route path="instruments" element={<Instruments />} />
            <Route path="ledgers/:ledgerSlug/instruments" element={<Instruments />} />
            <Route path="instrument-prices" element={<InstrumentPrices />} />
            <Route path="ledgers/:ledgerSlug/instrument-prices" element={<InstrumentPrices />} />
            <Route path="portfolio" element={<Portfolio />} />
            <Route path="ledgers/:ledgerSlug/portfolio" element={<Portfolio />} />
            <Route path="settings" element={<Settings />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

export default App
