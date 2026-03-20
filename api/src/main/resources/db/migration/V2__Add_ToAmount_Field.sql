alter table public.transaction_entries
add column to_amount numeric(20,2) null,
add column to_currency varchar(50) null;