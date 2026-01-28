-- Add slug column to accounts table
ALTER TABLE public.accounts ADD COLUMN slug varchar(255);

-- Create unique index for slug per ledger (accounts within a ledger must have unique slugs)
CREATE UNIQUE INDEX idx_accounts_ledger_slug ON public.accounts(ledger_id, slug);

-- Populate slug for existing accounts from name (lowercase, replace spaces with dashes, remove special chars)
UPDATE public.accounts SET slug = LOWER(REGEXP_REPLACE(REGEXP_REPLACE(name, '[^a-zA-Z0-9\s-]', '', 'g'), '\s+', '-', 'g'));

-- Make slug not null after populating
ALTER TABLE public.accounts ALTER COLUMN slug SET NOT NULL;
