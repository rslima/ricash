-- V5 seeded three sample rates "for testing" into every environment.
-- Remove them; real rates are entered manually or fetched from providers.
-- V5 itself must not be edited (Flyway checksum), so this compensating
-- migration deletes the seeded rows by their deterministic ids.
DELETE FROM exchange_rates
WHERE id IN ('er-usd-brl-20250130', 'er-eur-brl-20250130', 'er-usd-eur-20250130');
