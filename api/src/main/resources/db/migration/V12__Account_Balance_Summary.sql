-- Incrementally-maintained per-account balance rollup.
--
-- Replaces the recursive-CTE-over-transaction_entries balance computation used
-- by the account and ledger list/detail endpoints. Reads now sum this small
-- table instead of scanning transaction_entries, and triggers keep it exactly
-- in sync on every entry write, so balances stay immediately fresh.
--
-- Model: each transaction_entry contributes up to two "facets" keyed by the
-- currency the amount is denominated in:
--   * (account_id, currency)    += amount       (always)
--   * (account_id, to_currency) += to_amount    (when a conversion is present)
-- Reads select the facet whose currency matches the account currency, which
-- faithfully reproduces the currency-aware CASE in the original queries:
--     WHEN te.to_currency = a.currency THEN te.to_amount
--     WHEN te.currency    = a.currency THEN te.amount
--     ELSE 0
-- DEBIT and CREDIT are stored separately so the read can apply the per-account
-- sign convention (ASSET/EXPENSE: debit - credit; otherwise credit - debit).

CREATE TABLE public.account_balance_summary (
    account_id   VARCHAR(50)   NOT NULL,
    currency     VARCHAR(50)   NOT NULL,
    debit_total  NUMERIC(20,2) NOT NULL DEFAULT 0,
    credit_total NUMERIC(20,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, currency),
    FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE
);

-- One-time backfill from existing data. Both facets are unioned and aggregated
-- so they merge into the same (account_id, currency) bucket where they collide.
INSERT INTO public.account_balance_summary (account_id, currency, debit_total, credit_total)
SELECT account_id, currency, SUM(debit_amount), SUM(credit_amount)
FROM (
    SELECT account_id,
           currency,
           CASE WHEN type = 'DEBIT'  THEN amount ELSE 0 END AS debit_amount,
           CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END AS credit_amount
    FROM public.transaction_entries
    UNION ALL
    SELECT account_id,
           to_currency,
           CASE WHEN type = 'DEBIT'  THEN to_amount ELSE 0 END,
           CASE WHEN type = 'CREDIT' THEN to_amount ELSE 0 END
    FROM public.transaction_entries
    WHERE to_currency IS NOT NULL AND to_amount IS NOT NULL
) facets
GROUP BY account_id, currency;

-- Apply a signed delta to a single (account_id, currency) bucket.
CREATE OR REPLACE FUNCTION public.apply_account_balance_delta(
    p_account_id VARCHAR,
    p_currency   VARCHAR,
    p_debit      NUMERIC,
    p_credit     NUMERIC
) RETURNS void AS $$
BEGIN
    IF p_currency IS NULL OR (p_debit = 0 AND p_credit = 0) THEN
        RETURN;
    END IF;

    INSERT INTO public.account_balance_summary (account_id, currency, debit_total, credit_total)
    VALUES (p_account_id, p_currency, p_debit, p_credit)
    ON CONFLICT (account_id, currency) DO UPDATE
        SET debit_total  = public.account_balance_summary.debit_total  + EXCLUDED.debit_total,
            credit_total = public.account_balance_summary.credit_total + EXCLUDED.credit_total;
END;
$$ LANGUAGE plpgsql;

-- Maintain the summary on every transaction_entries write. UPDATE is handled as
-- subtract-old then add-new; the app currently edits transactions by deleting
-- and re-inserting entries, but this covers in-place updates too.
CREATE OR REPLACE FUNCTION public.transaction_entries_balance_summary() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'DELETE' OR TG_OP = 'UPDATE') THEN
        PERFORM public.apply_account_balance_delta(
            OLD.account_id, OLD.currency,
            CASE WHEN OLD.type = 'DEBIT'  THEN -OLD.amount ELSE 0 END,
            CASE WHEN OLD.type = 'CREDIT' THEN -OLD.amount ELSE 0 END);
        IF OLD.to_currency IS NOT NULL AND OLD.to_amount IS NOT NULL THEN
            PERFORM public.apply_account_balance_delta(
                OLD.account_id, OLD.to_currency,
                CASE WHEN OLD.type = 'DEBIT'  THEN -OLD.to_amount ELSE 0 END,
                CASE WHEN OLD.type = 'CREDIT' THEN -OLD.to_amount ELSE 0 END);
        END IF;
    END IF;

    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
        PERFORM public.apply_account_balance_delta(
            NEW.account_id, NEW.currency,
            CASE WHEN NEW.type = 'DEBIT'  THEN NEW.amount ELSE 0 END,
            CASE WHEN NEW.type = 'CREDIT' THEN NEW.amount ELSE 0 END);
        IF NEW.to_currency IS NOT NULL AND NEW.to_amount IS NOT NULL THEN
            PERFORM public.apply_account_balance_delta(
                NEW.account_id, NEW.to_currency,
                CASE WHEN NEW.type = 'DEBIT'  THEN NEW.to_amount ELSE 0 END,
                CASE WHEN NEW.type = 'CREDIT' THEN NEW.to_amount ELSE 0 END);
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transaction_entries_balance_summary_trg
    AFTER INSERT OR UPDATE OR DELETE ON public.transaction_entries
    FOR EACH ROW EXECUTE FUNCTION public.transaction_entries_balance_summary();
