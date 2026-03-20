-- Instruments table for tracking financial instruments (stocks, ETFs, bonds, etc.)
CREATE TABLE IF NOT EXISTS public.instruments (
    id VARCHAR(50) PRIMARY KEY,
    ledger_id VARCHAR(50) NOT NULL REFERENCES public.ledgers(id),
    symbol VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    currency VARCHAR(50) NOT NULL,
    market VARCHAR(50),
    isin VARCHAR(12),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (ledger_id, symbol)
);

CREATE INDEX idx_instruments_ledger_id ON public.instruments(ledger_id);
CREATE INDEX idx_instruments_symbol ON public.instruments(symbol);
CREATE INDEX idx_instruments_type ON public.instruments(type);
