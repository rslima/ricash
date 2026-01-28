-- Add slug column to ledgers table
ALTER TABLE public.ledgers ADD COLUMN slug varchar(255);

-- Create unique index for slug per user (users can have same slug names as other users)
CREATE UNIQUE INDEX idx_ledgers_user_slug ON public.ledgers(user_id, slug);

-- Populate slug for existing ledgers from name (lowercase, replace spaces with dashes)
UPDATE public.ledgers SET slug = LOWER(REGEXP_REPLACE(REGEXP_REPLACE(name, '[^a-zA-Z0-9\\s-]', '', 'g'), '\\s+', '-', 'g'));

-- Make slug not null after populating
ALTER TABLE public.ledgers ALTER COLUMN slug SET NOT NULL;
