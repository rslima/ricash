-- An account or envelope must never be its own parent. Before this migration
-- the API accepted such an update, and the account-tree CTEs (then UNION ALL)
-- recursed forever on it. The services now reject self/descendant parents;
-- this constraint is the database-level backstop for the self case.
--
-- Rows that already point at themselves are invisible in the tree (never a
-- root, never reachable from one), so detaching them to top level only makes
-- them visible again, and lets the constraint be added instead of failing
-- the migration on startup.

UPDATE public.accounts SET parent_account_id = NULL WHERE parent_account_id = id;
UPDATE public.envelopes SET parent_envelope_id = NULL WHERE parent_envelope_id = id;

ALTER TABLE public.accounts
    ADD CONSTRAINT chk_accounts_not_own_parent CHECK (parent_account_id IS NULL OR parent_account_id <> id);

ALTER TABLE public.envelopes
    ADD CONSTRAINT chk_envelopes_not_own_parent CHECK (parent_envelope_id IS NULL OR parent_envelope_id <> id);
