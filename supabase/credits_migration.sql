-- Ejecutar una vez en Supabase SQL Editor para activar el sistema de créditos.
-- Un crédito equivale a un análisis estándar de 50 reseñas en un idioma.

alter table public.daily_analysis_usage
  add column if not exists used_credits smallint not null default 0;

alter table public.daily_analysis_usage
  drop constraint if exists daily_analysis_usage_used_credits_check;

alter table public.daily_analysis_usage
  add constraint daily_analysis_usage_used_credits_check
  check (used_credits between 0 and 30);

create table if not exists public.free_credit_identity_usage (
  identity_hash text not null,
  usage_date date not null default current_date,
  used_credits smallint not null default 0 check (used_credits between 0 and 3),
  primary key (identity_hash, usage_date)
);

alter table public.free_credit_identity_usage enable row level security;
revoke all on table public.free_credit_identity_usage from public, anon, authenticated;

drop function if exists public.consume_analysis_credits(text, smallint);

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
  select profiles.plan into current_plan from public.profiles
    where id = p_user_id for update;
  credit_limit := case when current_plan = 'premium' then 30 else 3 end;

  insert into public.daily_analysis_usage (user_id, usage_date, used_credits)
    values (p_user_id, current_date, 0)
    on conflict (user_id, usage_date) do nothing;
  select used_credits into current_usage
    from public.daily_analysis_usage
    where user_id = p_user_id and usage_date = current_date for update;

  if current_plan = 'free' then
    if p_identity_hash is null or length(p_identity_hash) <> 64 then
      return query select false, 0::smallint, current_plan::text;
      return;
    end if;
    insert into public.free_credit_identity_usage (identity_hash, usage_date, used_credits)
      values (p_identity_hash, current_date, 0)
      on conflict (identity_hash, usage_date) do nothing;
    select used_credits into identity_usage
      from public.free_credit_identity_usage
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

  update public.daily_analysis_usage
    set used_credits = used_credits + p_credits
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
