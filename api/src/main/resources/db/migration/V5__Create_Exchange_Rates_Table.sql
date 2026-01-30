-- Create exchange_rates table for storing currency conversion rates
CREATE TABLE exchange_rates (
    id varchar(50) PRIMARY KEY,
    from_currency varchar(50) NOT NULL,
    to_currency varchar(50) NOT NULL,
    rate numeric(20, 6) NOT NULL,
    effective_date date NOT NULL,
    source varchar(255),
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exchange_rates_currency_date UNIQUE (from_currency, to_currency, effective_date),
    CONSTRAINT chk_rate_positive CHECK (rate > 0),
    CONSTRAINT chk_different_currencies CHECK (from_currency != to_currency)
);

-- Create index for efficient rate lookups
CREATE INDEX idx_exchange_rates_currencies_date ON exchange_rates (from_currency, to_currency, effective_date DESC);

-- Insert some sample rates for testing (BRL exchange rates as of 2025-01-30)
INSERT INTO exchange_rates (id, from_currency, to_currency, rate, effective_date, source) VALUES
    ('er-usd-brl-20250130', 'USD', 'BRL', 5.7500, '2025-01-30', 'MANUAL'),
    ('er-eur-brl-20250130', 'EUR', 'BRL', 6.0500, '2025-01-30', 'MANUAL'),
    ('er-usd-eur-20250130', 'USD', 'EUR', 0.9500, '2025-01-30', 'MANUAL');
