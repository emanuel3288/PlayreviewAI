-- PlayReview AI: ejecutar una sola vez en Supabase > SQL Editor.
-- Las cuentas se autentican con Firebase Auth. Supabase guarda sólo los datos de PlayReview.

create type public.subscription_plan as enum ('free', 'premium');

create table public.profiles (
  id text primary key,
  plan public.subscription_plan not null default 'free',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.apps (
  id text primary key,
  title text not null,
  developer text,
  icon_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.tracked_apps (
  user_id text not null references public.profiles(id) on delete cascade,
  app_id text not null references public.apps(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, app_id)
);

create table public.reviews (
  app_id text not null references public.apps(id) on delete cascade,
  source_review_id text not null,
  rating smallint not null check (rating between 1 and 5),
  content text not null default '',
  review_date timestamptz,
  region text not null default 'und',
  language text not null default 'es',
  first_seen_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  primary key (app_id, source_review_id)
);

create index reviews_app_date_idx on public.reviews (app_id, review_date desc);

alter table public.reviews enable row level security;

create table public.analysis_runs (
  id uuid primary key default gen_random_uuid(),
  user_id text not null references public.profiles(id) on delete cascade,
  app_id text not null references public.apps(id) on delete cascade,
  period_days smallint not null check (period_days in (7, 14, 30)),
  review_count smallint not null check (review_count between 0 and 500),
  average_score numeric(3, 2) not null,
  sentiments jsonb not null,
  top_categories jsonb not null,
  week_trend jsonb not null,
  month_trend jsonb not null,
  created_at timestamptz not null default now()
);

create index analysis_runs_user_app_created_idx
  on public.analysis_runs (user_id, app_id, created_at desc);

create table public.daily_analysis_usage (
  user_id text not null references public.profiles(id) on delete cascade,
  usage_date date not null default current_date,
  used smallint not null default 0 check (used between 0 and 30),
  used_credits smallint not null default 0 check (used_credits between 0 and 30),
  primary key (user_id, usage_date)
);

create table public.free_credit_identity_usage (
  identity_hash text not null,
  usage_date date not null default current_date,
  used_credits smallint not null default 0 check (used_credits between 0 and 3),
  primary key (identity_hash, usage_date)
);

-- Consume una cuota de forma atómica. Premium se conectará a esta misma
-- función cambiando la condición según profiles.plan.
create or replace function public.consume_free_analysis(p_user_id text)
returns table (allowed boolean, remaining smallint)
language plpgsql
security definer set search_path = public
as $$
declare
  current_usage smallint;
begin
  insert into public.profiles (id) values (p_user_id)
  on conflict (id) do nothing;

  insert into public.daily_analysis_usage (user_id, usage_date, used)
  values (p_user_id, current_date, 1)
  on conflict (user_id, usage_date) do nothing
  returning used into current_usage;

  if found then
    return query select true, 2::smallint;
    return;
  end if;

  select used into current_usage
  from public.daily_analysis_usage
  where user_id = p_user_id and usage_date = current_date
  for update;

  if current_usage >= 3 then
    return query select false, 0::smallint;
    return;
  end if;

  update public.daily_analysis_usage
  set used = used + 1
  where user_id = p_user_id and usage_date = current_date
  returning used into current_usage;

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

-- En instalaciones nuevas, incluye la cuota Premium. En bases ya existentes,
-- ejecutar supabase/premium_analysis_migration.sql.
create or replace function public.consume_analysis(
  p_user_id text, p_max_reviews integer, p_language_count integer
)
returns table (allowed boolean, remaining smallint, plan text)
language plpgsql
security definer set search_path = public
as $$
declare
  current_plan public.subscription_plan;
  current_usage smallint;
  daily_limit smallint;
begin
  insert into public.profiles (id) values (p_user_id) on conflict (id) do nothing;
  select profiles.plan into current_plan from public.profiles
    where id = p_user_id for update;
  daily_limit := case when current_plan = 'premium' then 30 else 3 end;
  if p_max_reviews not in (50, 100, 500)
      or p_language_count not between 1 and 3
      or (current_plan = 'free' and (p_max_reviews <> 50 or p_language_count <> 1)) then
    return query select false, 0::smallint, current_plan::text;
    return;
  end if;
  insert into public.daily_analysis_usage (user_id, usage_date, used)
    values (p_user_id, current_date, 0) on conflict (user_id, usage_date) do nothing;
  select used into current_usage from public.daily_analysis_usage
    where user_id = p_user_id and usage_date = current_date for update;
  if current_usage >= daily_limit then
    return query select false, 0::smallint, current_plan::text;
    return;
  end if;
  update public.daily_analysis_usage set used = used + 1
    where user_id = p_user_id and usage_date = current_date;
  return query select true, (daily_limit - current_usage - 1)::smallint, current_plan::text;
end;
$$;

revoke execute on function public.consume_analysis(text, integer, integer) from public;
grant execute on function public.consume_analysis(text, integer, integer) to service_role;

create or replace function public.consume_analysis_credits(
  p_user_id text, p_credits smallint, p_identity_hash text
)
returns table (allowed boolean, remaining smallint, plan text)
language plpgsql
security definer set search_path = public
as $$
declare
  current_plan public.subscription_plan;
  current_usage smallint;
  identity_usage smallint;
  credit_limit smallint;
begin
  if p_credits not between 1 and 5 then
    return query select false, 0::smallint, 'free'::text;
    return;
  end if;
  insert into public.profiles (id) values (p_user_id) on conflict (id) do nothing;
  select profiles.plan into current_plan from public.profiles where id = p_user_id for update;
  credit_limit := case when current_plan = 'premium' then 30 else 3 end;
  insert into public.daily_analysis_usage (user_id, usage_date, used_credits)
    values (p_user_id, current_date, 0) on conflict (user_id, usage_date) do nothing;
  select used_credits into current_usage from public.daily_analysis_usage
    where user_id = p_user_id and usage_date = current_date for update;
  if current_plan = 'free' then
    if p_identity_hash is null or length(p_identity_hash) <> 64 then
      return query select false, 0::smallint, current_plan::text;
      return;
    end if;
    insert into public.free_credit_identity_usage (identity_hash, usage_date, used_credits)
      values (p_identity_hash, current_date, 0) on conflict (identity_hash, usage_date) do nothing;
    select used_credits into identity_usage from public.free_credit_identity_usage
      where identity_hash = p_identity_hash and usage_date = current_date for update;
    current_usage := greatest(current_usage, identity_usage);
  end if;
  if current_plan = 'free' and p_credits <> 1 then
    return query select false, greatest(credit_limit - current_usage, 0)::smallint, current_plan::text;
    return;
  end if;
  if current_usage + p_credits > credit_limit then
    return query select false, greatest(credit_limit - current_usage, 0)::smallint, current_plan::text;
    return;
  end if;
  update public.daily_analysis_usage set used_credits = used_credits + p_credits
    where user_id = p_user_id and usage_date = current_date;
  if current_plan = 'free' then
    update public.free_credit_identity_usage
      set used_credits = greatest(used_credits, current_usage + p_credits)
      where identity_hash = p_identity_hash and usage_date = current_date;
  end if;
  return query select true, (credit_limit - current_usage - p_credits)::smallint, current_plan::text;
end;
$$;

revoke execute on function public.consume_analysis_credits(text, smallint, text) from public;
grant execute on function public.consume_analysis_credits(text, smallint, text) to service_role;
