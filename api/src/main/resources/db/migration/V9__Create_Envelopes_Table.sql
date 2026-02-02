-- Envelopes (budget categories)
create table if not exists public.envelopes (
  id varchar(50) primary key,
  ledger_id varchar(50) not null,
  parent_envelope_id varchar(50),
  name varchar(255) not null,
  description text,
  currency varchar(50) not null,
  type varchar(50) not null,
  status varchar(50) not null default 'ACTIVE',
  created_at timestamp not null default current_timestamp,
  foreign key (ledger_id) references public.ledgers(id),
  foreign key (parent_envelope_id) references public.envelopes(id)
);

-- Monthly allocations (budgeted amounts)
create table if not exists public.envelope_allocations (
  id varchar(50) primary key,
  envelope_id varchar(50) not null,
  period_year int not null,
  period_month int not null,
  allocated_amount numeric(20, 2) not null default 0,
  notes text,
  created_at timestamp not null default current_timestamp,
  updated_at timestamp not null default current_timestamp,
  foreign key (envelope_id) references public.envelopes(id),
  unique (envelope_id, period_year, period_month)
);

-- Link transactions to envelopes
alter table public.transaction_entries
  add column envelope_id varchar(50) references public.envelopes(id);

-- Auto-link accounts to envelopes (optional mapping)
create table if not exists public.envelope_account_mappings (
  id varchar(50) primary key,
  envelope_id varchar(50) not null,
  account_id varchar(50) not null,
  foreign key (envelope_id) references public.envelopes(id),
  foreign key (account_id) references public.accounts(id),
  unique (account_id)
);
