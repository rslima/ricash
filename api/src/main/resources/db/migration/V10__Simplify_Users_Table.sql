drop table if exists public.user_roles;
drop table if exists public.roles;

alter table public.users drop column if exists username;
alter table public.users drop column if exists password;
alter table public.users drop column if exists salt;
alter table public.users drop column if exists status;
alter table public.users drop column if exists email;
alter table public.users drop column if exists created_at;
