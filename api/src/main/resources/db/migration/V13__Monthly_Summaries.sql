-- Incrementally-maintained per-month rollups for the monthly expense/income
-- breakdown endpoints and the envelope "spent this month" calculation. Like V12,
-- these are kept exactly fresh by triggers so reads avoid scanning a month of
-- transaction_entries.
--
-- Two tables because the queries use two different amount conventions:
--
--  * monthly_account_summary  (breakdowns) — currency-aware two-facet model,
--    identical to account_balance_summary but bucketed by (year, month). Reads
--    roll up the account subtree and apply the per-account-type sign.
--
--  * envelope_monthly_summary (envelope spent) — "effective amount" convention:
--    COALESCE(to_amount, amount) in COALESCE(to_currency, currency), DEBIT only.
--
-- The bucket period comes from the entry's TRANSACTION date, so the entry trigger
-- looks that date up, and a trigger on transactions re-buckets every entry when a
-- transaction's date changes (the app updates transactions.date before rewriting
-- its entries, so the entry triggers alone would otherwise use the wrong period).

CREATE TABLE public.monthly_account_summary (
    account_id   VARCHAR(50)   NOT NULL,
    period_year  INT           NOT NULL,
    period_month INT           NOT NULL,
    currency     VARCHAR(50)   NOT NULL,
    debit_total  NUMERIC(20,2) NOT NULL DEFAULT 0,
    credit_total NUMERIC(20,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, period_year, period_month, currency),
    FOREIGN KEY (account_id) REFERENCES public.accounts(id) ON DELETE CASCADE
);

CREATE TABLE public.envelope_monthly_summary (
    envelope_id  VARCHAR(50)   NOT NULL,
    period_year  INT           NOT NULL,
    period_month INT           NOT NULL,
    currency     VARCHAR(50)   NOT NULL,
    spent_total  NUMERIC(20,2) NOT NULL DEFAULT 0,
    PRIMARY KEY (envelope_id, period_year, period_month, currency),
    FOREIGN KEY (envelope_id) REFERENCES public.envelopes(id) ON DELETE CASCADE
);

-- ---- Delta helpers (upsert; skip no-ops) ---------------------------------

CREATE OR REPLACE FUNCTION public.apply_monthly_account_delta(
    p_account_id VARCHAR, p_year INT, p_month INT, p_currency VARCHAR,
    p_debit NUMERIC, p_credit NUMERIC
) RETURNS void AS $$
BEGIN
    IF p_currency IS NULL OR (p_debit = 0 AND p_credit = 0) THEN
        RETURN;
    END IF;
    INSERT INTO public.monthly_account_summary
        (account_id, period_year, period_month, currency, debit_total, credit_total)
    VALUES (p_account_id, p_year, p_month, p_currency, p_debit, p_credit)
    ON CONFLICT (account_id, period_year, period_month, currency) DO UPDATE
        SET debit_total  = public.monthly_account_summary.debit_total  + EXCLUDED.debit_total,
            credit_total = public.monthly_account_summary.credit_total + EXCLUDED.credit_total;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION public.apply_envelope_monthly_delta(
    p_envelope_id VARCHAR, p_year INT, p_month INT, p_currency VARCHAR, p_spent NUMERIC
) RETURNS void AS $$
BEGIN
    IF p_envelope_id IS NULL OR p_currency IS NULL OR p_spent = 0 THEN
        RETURN;
    END IF;
    INSERT INTO public.envelope_monthly_summary
        (envelope_id, period_year, period_month, currency, spent_total)
    VALUES (p_envelope_id, p_year, p_month, p_currency, p_spent)
    ON CONFLICT (envelope_id, period_year, period_month, currency) DO UPDATE
        SET spent_total = public.envelope_monthly_summary.spent_total + EXCLUDED.spent_total;
END;
$$ LANGUAGE plpgsql;

-- Apply one entry's contribution to both monthly tables for a given period,
-- scaled by p_sign (+1 to add, -1 to remove).
CREATE OR REPLACE FUNCTION public.apply_entry_monthly(
    e public.transaction_entries, p_year INT, p_month INT, p_sign NUMERIC
) RETURNS void AS $$
BEGIN
    -- monthly_account_summary, facet 1 (base currency/amount)
    PERFORM public.apply_monthly_account_delta(
        e.account_id, p_year, p_month, e.currency,
        p_sign * CASE WHEN e.type = 'DEBIT'  THEN e.amount ELSE 0 END,
        p_sign * CASE WHEN e.type = 'CREDIT' THEN e.amount ELSE 0 END);

    -- monthly_account_summary, facet 2 (converted to_currency/to_amount)
    IF e.to_currency IS NOT NULL AND e.to_amount IS NOT NULL THEN
        PERFORM public.apply_monthly_account_delta(
            e.account_id, p_year, p_month, e.to_currency,
            p_sign * CASE WHEN e.type = 'DEBIT'  THEN e.to_amount ELSE 0 END,
            p_sign * CASE WHEN e.type = 'CREDIT' THEN e.to_amount ELSE 0 END);
    END IF;

    -- envelope spent: DEBIT entries tagged with an envelope, effective amount
    IF e.envelope_id IS NOT NULL AND e.type = 'DEBIT' THEN
        PERFORM public.apply_envelope_monthly_delta(
            e.envelope_id, p_year, p_month,
            COALESCE(e.to_currency, e.currency),
            p_sign * COALESCE(e.to_amount, e.amount));
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ---- Backfill ------------------------------------------------------------

INSERT INTO public.monthly_account_summary
    (account_id, period_year, period_month, currency, debit_total, credit_total)
SELECT account_id, period_year, period_month, currency,
       SUM(debit_amount), SUM(credit_amount)
FROM (
    SELECT te.account_id,
           EXTRACT(YEAR  FROM t.date)::int AS period_year,
           EXTRACT(MONTH FROM t.date)::int AS period_month,
           te.currency,
           CASE WHEN te.type = 'DEBIT'  THEN te.amount ELSE 0 END AS debit_amount,
           CASE WHEN te.type = 'CREDIT' THEN te.amount ELSE 0 END AS credit_amount
    FROM public.transaction_entries te
    JOIN public.transactions t ON t.id = te.transaction_id
    UNION ALL
    SELECT te.account_id,
           EXTRACT(YEAR  FROM t.date)::int,
           EXTRACT(MONTH FROM t.date)::int,
           te.to_currency,
           CASE WHEN te.type = 'DEBIT'  THEN te.to_amount ELSE 0 END,
           CASE WHEN te.type = 'CREDIT' THEN te.to_amount ELSE 0 END
    FROM public.transaction_entries te
    JOIN public.transactions t ON t.id = te.transaction_id
    WHERE te.to_currency IS NOT NULL AND te.to_amount IS NOT NULL
) facets
GROUP BY account_id, period_year, period_month, currency;

INSERT INTO public.envelope_monthly_summary
    (envelope_id, period_year, period_month, currency, spent_total)
SELECT te.envelope_id,
       EXTRACT(YEAR  FROM t.date)::int,
       EXTRACT(MONTH FROM t.date)::int,
       COALESCE(te.to_currency, te.currency),
       SUM(COALESCE(te.to_amount, te.amount))
FROM public.transaction_entries te
JOIN public.transactions t ON t.id = te.transaction_id
WHERE te.envelope_id IS NOT NULL AND te.type = 'DEBIT'
GROUP BY te.envelope_id,
         EXTRACT(YEAR FROM t.date)::int, EXTRACT(MONTH FROM t.date)::int,
         COALESCE(te.to_currency, te.currency);

-- ---- Triggers ------------------------------------------------------------

-- Maintain monthly tables on entry writes. Period = the entry's transaction date.
CREATE OR REPLACE FUNCTION public.transaction_entries_monthly_summary() RETURNS trigger AS $$
DECLARE
    y INT;
    m INT;
BEGIN
    IF (TG_OP = 'DELETE' OR TG_OP = 'UPDATE') THEN
        SELECT EXTRACT(YEAR FROM date)::int, EXTRACT(MONTH FROM date)::int
          INTO y, m FROM public.transactions WHERE id = OLD.transaction_id;
        IF FOUND THEN
            PERFORM public.apply_entry_monthly(OLD, y, m, -1);
        END IF;
    END IF;

    IF (TG_OP = 'INSERT' OR TG_OP = 'UPDATE') THEN
        SELECT EXTRACT(YEAR FROM date)::int, EXTRACT(MONTH FROM date)::int
          INTO y, m FROM public.transactions WHERE id = NEW.transaction_id;
        IF FOUND THEN
            PERFORM public.apply_entry_monthly(NEW, y, m, 1);
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transaction_entries_monthly_summary_trg
    AFTER INSERT OR UPDATE OR DELETE ON public.transaction_entries
    FOR EACH ROW EXECUTE FUNCTION public.transaction_entries_monthly_summary();

-- Re-bucket all of a transaction's entries when its date moves to another month.
CREATE OR REPLACE FUNCTION public.transactions_monthly_summary() RETURNS trigger AS $$
DECLARE
    old_y INT := EXTRACT(YEAR  FROM OLD.date)::int;
    old_m INT := EXTRACT(MONTH FROM OLD.date)::int;
    new_y INT := EXTRACT(YEAR  FROM NEW.date)::int;
    new_m INT := EXTRACT(MONTH FROM NEW.date)::int;
    rec   public.transaction_entries;
BEGIN
    IF old_y = new_y AND old_m = new_m THEN
        RETURN NULL;
    END IF;

    FOR rec IN SELECT * FROM public.transaction_entries WHERE transaction_id = NEW.id LOOP
        PERFORM public.apply_entry_monthly(rec, old_y, old_m, -1);
        PERFORM public.apply_entry_monthly(rec, new_y, new_m, 1);
    END LOOP;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER transactions_monthly_summary_trg
    AFTER UPDATE OF date ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION public.transactions_monthly_summary();
