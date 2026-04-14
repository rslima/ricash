-- Foreign-key and hot-query indexes for list/lookup endpoints.

-- Ledgers: list user's ledgers.
CREATE INDEX IF NOT EXISTS idx_ledgers_user_id
    ON public.ledgers(user_id);

-- Accounts: list by ledger, ordered by name; hierarchy traversal by parent.
CREATE INDEX IF NOT EXISTS idx_accounts_ledger_id
    ON public.accounts(ledger_id);
CREATE INDEX IF NOT EXISTS idx_accounts_ledger_parent
    ON public.accounts(ledger_id, parent_account_id);

-- Transactions: ledger listing paginated by (date DESC, created_at DESC).
CREATE INDEX IF NOT EXISTS idx_transactions_ledger_date
    ON public.transactions(ledger_id, date DESC, created_at DESC);

-- Transaction entries: FK lookups used by every transaction join, account
-- transaction list, budget spent rollups, and account-delete guard.
CREATE INDEX IF NOT EXISTS idx_transaction_entries_transaction_id
    ON public.transaction_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_entries_account_id
    ON public.transaction_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_transaction_entries_envelope_id
    ON public.transaction_entries(envelope_id)
    WHERE envelope_id IS NOT NULL;

-- Envelopes: list by ledger and hierarchy traversal.
CREATE INDEX IF NOT EXISTS idx_envelopes_ledger_parent
    ON public.envelopes(ledger_id, parent_envelope_id);

-- Envelope allocations: cross-envelope period aggregation.
CREATE INDEX IF NOT EXISTS idx_envelope_allocations_period
    ON public.envelope_allocations(period_year, period_month);

-- Envelope/account mappings: reverse lookup by envelope (account_id already unique).
CREATE INDEX IF NOT EXISTS idx_envelope_account_mappings_envelope_id
    ON public.envelope_account_mappings(envelope_id);

-- Instrument prices: "price at or before date" uses leading instrument_id
-- with effective_date DESC; this composite supersedes the existing singletons
-- for the main query path but we leave those in place to stay additive.
CREATE INDEX IF NOT EXISTS idx_instrument_prices_instrument_effective
    ON public.instrument_prices(instrument_id, effective_date DESC);

-- Exchange rates: scans by effective date across pairs.
CREATE INDEX IF NOT EXISTS idx_exchange_rates_effective_date
    ON public.exchange_rates(effective_date DESC);
