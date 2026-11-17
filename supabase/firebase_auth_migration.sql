-- Ejecutar una vez en Supabase SQL Editor antes de desplegar el backend Firebase.
-- Conserva los análisis existentes, pero los desvincula de Supabase Auth para que
-- puedan identificarse por Firebase UID (texto).

drop policy if exists "users can read their own profile" on public.profiles;
drop policy if exists "users can read their tracked apps" on public.tracked_apps;
drop policy if exists "users can read their analysis history" on public.analysis_runs;

drop trigger if exists on_auth_user_created on auth.users;
drop function if exists public.create_profile_for_new_user();

alter table public.tracked_apps drop constraint if exists tracked_apps_user_id_fkey;
alter table public.analysis_runs drop constraint if exists analysis_runs_user_id_fkey;
alter table public.daily_analysis_usage drop constraint if exists daily_analysis_usage_user_id_fkey;
alter table public.profiles drop constraint if exists profiles_pkey;
alter table public.profiles drop constraint if exists profiles_id_fkey;

alter table public.profiles alter column id type text using id::text;
alter table public.tracked_apps alter column user_id type text using user_id::text;
alter table public.analysis_runs alter column user_id type text using user_id::text;
alter table public.daily_analysis_usage alter column user_id type text using user_id::text;

alter table public.profiles add primary key (id);
alter table public.tracked_apps add constraint tracked_apps_user_id_fkey
  foreign key (user_id) references public.profiles(id) on delete cascade;
alter table public.analysis_runs add constraint analysis_runs_user_id_fkey
  foreign key (user_id) references public.profiles(id) on delete cascade;
alter table public.daily_analysis_usage add constraint daily_analysis_usage_user_id_fkey
  foreign key (user_id) references public.profiles(id) on delete cascade;

drop function if exists public.consume_free_analysis(uuid);
create or replace function public.consume_free_analysis(p_user_id text)
returns table (allowed boolean, remaining smallint)
language plpgsql
security definer set search_path = public
as $$
declare current_usage smallint;
begin
  insert into public.profiles (id) values (p_user_id) on conflict (id) do nothing;
  insert into public.daily_analysis_usage (user_id, usage_date, used)
  values (p_user_id, current_date, 1)
  on conflict (user_id, usage_date) do nothing
  returning used into current_usage;
  if found then return query select true, 2::smallint; return; end if;
  select used into current_usage from public.daily_analysis_usage
    where user_id = p_user_id and usage_date = current_date for update;
  if current_usage >= 3 then return query select false, 0::smallint; return; end if;
  update public.daily_analysis_usage set used = used + 1
    where user_id = p_user_id and usage_date = current_date returning used into current_usage;
  return query select true, (3 - current_usage)::smallint;
end;
$$;

alter table public.profiles enable row level security;
alter table public.tracked_apps enable row level security;
alter table public.analysis_runs enable row level security;

create policy "users can read their own profile"
  on public.profiles for select using (auth.uid()::text = id);
create policy "users can read their tracked apps"
  on public.tracked_apps for select using (auth.uid()::text = user_id);
create policy "users can read their analysis history"
  on public.analysis_runs for select using (auth.uid()::text = user_id);

revoke execute on function public.consume_free_analysis(text) from public;
grant execute on function public.consume_free_analysis(text) to service_role;
