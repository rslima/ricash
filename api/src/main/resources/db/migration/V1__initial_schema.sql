create table if not exists public.users (
  id varchar(50) primary key,
  username varchar(255) not null,
  password varchar(255) not null,
  salt varchar(255) not null,
  status varchar(255) not null,
  email varchar(255) not null,
  created_at timestamp not null default current_timestamp
);

create table if not exists public.roles (
  id varchar(50) primary key,
  name varchar(255) not null,
  description varchar(255) not null,
  created_at timestamp not null default current_timestamp
);

create table if not exists public.user_roles (
  user_id varchar(50) not null,
  role_id varchar(50) not null,
  created_at timestamp not null default current_timestamp,
  primary key (user_id, role_id),
  foreign key (user_id) references public.users(id),
  foreign key (role_id) references public.roles(id)

);

create table if not exists public.ledgers (
  id varchar(50) primary key,
  user_id varchar(50) not null,
  name varchar(255) not null,
  description text,
  currency varchar(50) not null,
  created_at timestamp not null default current_timestamp,
  foreign key (user_id) references public.users(id)
);

create table if not exists public.accounts (
  id varchar(50) primary key,
  ledger_id varchar(50) not null,
  parent_account_id varchar(50),
  name varchar(255) not null,
  description text,
  currency varchar(50) not null,
  type varchar(50) not null,
  status varchar(50) not null,
  created_at timestamp not null default current_timestamp,
  foreign key (ledger_id) references public.ledgers(id),
  foreign key (parent_account_id) references public.accounts(id)
);

create table if not exists public.transactions (
  id varchar(50) primary key,
  ledger_id varchar(50) not null,
  description text,
  date date not null,
  created_at timestamp not null default current_timestamp,
  foreign key (ledger_id) references public.ledgers(id)
);

create table if not exists public.transaction_entries (
  id varchar(50) primary key,
  transaction_id varchar(50) not null,
  account_id varchar(50) not null,
  amount numeric(20, 2) not null,
  type varchar(10) not null,
  currency varchar(50) not null,
  foreign key (transaction_id) references public.transactions(id),
  foreign key (account_id) references public.accounts(id)
);
