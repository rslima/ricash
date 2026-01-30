-- Add instrument tracking to transaction entries
ALTER TABLE public.transaction_entries
    ADD COLUMN instrument_id VARCHAR(50) REFERENCES public.instruments(id),
    ADD COLUMN quantity NUMERIC(20, 8);

CREATE INDEX idx_transaction_entries_instrument_id ON public.transaction_entries(instrument_id);
