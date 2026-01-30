-- Instrument prices table for historical price tracking
CREATE TABLE IF NOT EXISTS public.instrument_prices (
    id VARCHAR(50) PRIMARY KEY,
    instrument_id VARCHAR(50) NOT NULL REFERENCES public.instruments(id) ON DELETE CASCADE,
    price NUMERIC(20, 6) NOT NULL,
    effective_date DATE NOT NULL,
    source VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (instrument_id, effective_date)
);

CREATE INDEX idx_instrument_prices_instrument_id ON public.instrument_prices(instrument_id);
CREATE INDEX idx_instrument_prices_effective_date ON public.instrument_prices(effective_date);
